#include <EGL/egl.h>
#include <GLES2/gl2.h>
#include <android/bitmap.h>
#include <android/log.h>
#include <android/native_window_jni.h>
#include <jni.h>

#include <algorithm>
#include <cstdint>
#include <mutex>
#include <vector>

namespace {

constexpr const char* kLogTag = "MaaPreviewRenderer";

#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, kLogTag, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, kLogTag, __VA_ARGS__)

struct Renderer {
    ANativeWindow* window = nullptr;
    EGLDisplay display = EGL_NO_DISPLAY;
    EGLContext context = EGL_NO_CONTEXT;
    EGLSurface surface = EGL_NO_SURFACE;
    GLuint program = 0;
    GLuint texture = 0;
    GLint positionLoc = -1;
    GLint texCoordLoc = -1;
    GLint samplerLoc = -1;
    int surfaceWidth = 0;
    int surfaceHeight = 0;
};

std::mutex gMutex;
Renderer gRenderer;

const char* kVertexShader =
        "attribute vec2 aPosition;\n"
        "attribute vec2 aTexCoord;\n"
        "varying vec2 vTexCoord;\n"
        "void main() {\n"
        "  gl_Position = vec4(aPosition, 0.0, 1.0);\n"
        "  vTexCoord = aTexCoord;\n"
        "}\n";

const char* kFragmentShader =
        "precision mediump float;\n"
        "varying vec2 vTexCoord;\n"
        "uniform sampler2D uTexture;\n"
        "void main() {\n"
        "  gl_FragColor = texture2D(uTexture, vTexCoord);\n"
        "}\n";

GLuint compileShader(GLenum type, const char* source) {
    GLuint shader = glCreateShader(type);
    glShaderSource(shader, 1, &source, nullptr);
    glCompileShader(shader);
    GLint ok = GL_FALSE;
    glGetShaderiv(shader, GL_COMPILE_STATUS, &ok);
    if (ok != GL_TRUE) {
        glDeleteShader(shader);
        return 0;
    }
    return shader;
}

GLuint createProgram() {
    GLuint vertex = compileShader(GL_VERTEX_SHADER, kVertexShader);
    GLuint fragment = compileShader(GL_FRAGMENT_SHADER, kFragmentShader);
    if (vertex == 0 || fragment == 0) {
        if (vertex != 0) {
            glDeleteShader(vertex);
        }
        if (fragment != 0) {
            glDeleteShader(fragment);
        }
        return 0;
    }
    GLuint program = glCreateProgram();
    glAttachShader(program, vertex);
    glAttachShader(program, fragment);
    glLinkProgram(program);
    glDeleteShader(vertex);
    glDeleteShader(fragment);
    GLint ok = GL_FALSE;
    glGetProgramiv(program, GL_LINK_STATUS, &ok);
    if (ok != GL_TRUE) {
        glDeleteProgram(program);
        return 0;
    }
    return program;
}

void releaseRendererLocked() {
    if (gRenderer.display != EGL_NO_DISPLAY && gRenderer.context != EGL_NO_CONTEXT) {
        eglMakeCurrent(gRenderer.display, gRenderer.surface, gRenderer.surface, gRenderer.context);
        if (gRenderer.texture != 0) {
            glDeleteTextures(1, &gRenderer.texture);
        }
        if (gRenderer.program != 0) {
            glDeleteProgram(gRenderer.program);
        }
    }
    if (gRenderer.display != EGL_NO_DISPLAY) {
        eglMakeCurrent(gRenderer.display, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
        if (gRenderer.surface != EGL_NO_SURFACE) {
            eglDestroySurface(gRenderer.display, gRenderer.surface);
        }
        if (gRenderer.context != EGL_NO_CONTEXT) {
            eglDestroyContext(gRenderer.display, gRenderer.context);
        }
        eglTerminate(gRenderer.display);
    }
    if (gRenderer.window != nullptr) {
        ANativeWindow_release(gRenderer.window);
    }
    gRenderer = Renderer();
}

bool initRendererLocked(ANativeWindow* window) {
    releaseRendererLocked();
    if (window == nullptr) {
        LOGE("init failed: null window");
        return false;
    }
    gRenderer.window = window;
    ANativeWindow_acquire(gRenderer.window);
    gRenderer.surfaceWidth = ANativeWindow_getWidth(gRenderer.window);
    gRenderer.surfaceHeight = ANativeWindow_getHeight(gRenderer.window);
    LOGI("init window size=%dx%d", gRenderer.surfaceWidth, gRenderer.surfaceHeight);

    gRenderer.display = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    if (gRenderer.display == EGL_NO_DISPLAY) {
        LOGE("init failed: eglGetDisplay error=0x%x", eglGetError());
        return false;
    }
    if (eglInitialize(gRenderer.display, nullptr, nullptr) != EGL_TRUE) {
        LOGE("init failed: eglInitialize error=0x%x", eglGetError());
        return false;
    }

    const EGLint configAttribs[] = {
            EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
            EGL_SURFACE_TYPE, EGL_WINDOW_BIT,
            EGL_RED_SIZE, 8,
            EGL_GREEN_SIZE, 8,
            EGL_BLUE_SIZE, 8,
            EGL_ALPHA_SIZE, 8,
            EGL_NONE
    };
    EGLConfig config = nullptr;
    EGLint configCount = 0;
    if (eglChooseConfig(gRenderer.display, configAttribs, &config, 1, &configCount) != EGL_TRUE
            || configCount <= 0) {
        LOGE("init failed: eglChooseConfig count=%d error=0x%x", configCount, eglGetError());
        return false;
    }

    const EGLint contextAttribs[] = {
            EGL_CONTEXT_CLIENT_VERSION, 2,
            EGL_NONE
    };
    gRenderer.context = eglCreateContext(gRenderer.display, config, EGL_NO_CONTEXT, contextAttribs);
    if (gRenderer.context == EGL_NO_CONTEXT) {
        LOGE("init failed: eglCreateContext error=0x%x", eglGetError());
        return false;
    }
    gRenderer.surface = eglCreateWindowSurface(gRenderer.display, config, gRenderer.window, nullptr);
    if (gRenderer.surface == EGL_NO_SURFACE) {
        LOGE("init failed: eglCreateWindowSurface error=0x%x", eglGetError());
        return false;
    }
    if (eglMakeCurrent(gRenderer.display, gRenderer.surface, gRenderer.surface, gRenderer.context) != EGL_TRUE) {
        LOGE("init failed: eglMakeCurrent error=0x%x", eglGetError());
        return false;
    }

    gRenderer.program = createProgram();
    if (gRenderer.program == 0) {
        LOGE("init failed: createProgram glError=0x%x", glGetError());
        return false;
    }
    gRenderer.positionLoc = glGetAttribLocation(gRenderer.program, "aPosition");
    gRenderer.texCoordLoc = glGetAttribLocation(gRenderer.program, "aTexCoord");
    gRenderer.samplerLoc = glGetUniformLocation(gRenderer.program, "uTexture");
    glGenTextures(1, &gRenderer.texture);
    glBindTexture(GL_TEXTURE_2D, gRenderer.texture);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
    glClear(GL_COLOR_BUFFER_BIT);
    eglSwapBuffers(gRenderer.display, gRenderer.surface);
    eglMakeCurrent(gRenderer.display, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
    LOGI("init ready surface=%dx%d", gRenderer.surfaceWidth, gRenderer.surfaceHeight);
    return true;
}

bool renderBitmapLocked(JNIEnv* env, jobject bitmap) {
    if (gRenderer.display == EGL_NO_DISPLAY || gRenderer.surface == EGL_NO_SURFACE
            || gRenderer.context == EGL_NO_CONTEXT || bitmap == nullptr) {
        LOGE("render failed: renderer not ready display=%p surface=%p context=%p bitmap=%p",
                gRenderer.display, gRenderer.surface, gRenderer.context, bitmap);
        return false;
    }
    if (eglMakeCurrent(gRenderer.display, gRenderer.surface, gRenderer.surface, gRenderer.context) != EGL_TRUE) {
        LOGE("render failed: eglMakeCurrent error=0x%x", eglGetError());
        return false;
    }

    AndroidBitmapInfo info{};
    if (AndroidBitmap_getInfo(env, bitmap, &info) != ANDROID_BITMAP_RESULT_SUCCESS) {
        LOGE("render failed: AndroidBitmap_getInfo");
        return false;
    }
    if (info.width == 0 || info.height == 0) {
        LOGE("render failed: invalid bitmap size=%ux%u", info.width, info.height);
        return false;
    }
    if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888
            && info.format != ANDROID_BITMAP_FORMAT_RGB_565) {
        LOGE("render failed: unsupported bitmap format=%d", info.format);
        return false;
    }
    void* pixels = nullptr;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) != ANDROID_BITMAP_RESULT_SUCCESS || pixels == nullptr) {
        LOGE("render failed: AndroidBitmap_lockPixels");
        return false;
    }

    std::vector<uint32_t> converted;
    const void* uploadPixels = pixels;
    GLenum uploadFormat = GL_RGBA;
    GLenum uploadType = GL_UNSIGNED_BYTE;
    if (info.format == ANDROID_BITMAP_FORMAT_RGB_565) {
        const uint16_t* src = static_cast<const uint16_t*>(pixels);
        converted.resize(static_cast<size_t>(info.width) * static_cast<size_t>(info.height));
        for (uint32_t y = 0; y < info.height; ++y) {
            const uint16_t* row = reinterpret_cast<const uint16_t*>(
                    reinterpret_cast<const uint8_t*>(src) + static_cast<size_t>(y) * info.stride);
            for (uint32_t x = 0; x < info.width; ++x) {
                uint16_t v = row[x];
                uint8_t r = static_cast<uint8_t>(((v >> 11) & 0x1f) * 255 / 31);
                uint8_t g = static_cast<uint8_t>(((v >> 5) & 0x3f) * 255 / 63);
                uint8_t b = static_cast<uint8_t>((v & 0x1f) * 255 / 31);
                converted[static_cast<size_t>(y) * info.width + x] =
                        0xff000000u | (static_cast<uint32_t>(r) << 16)
                        | (static_cast<uint32_t>(g) << 8) | b;
            }
        }
        uploadPixels = converted.data();
    } else if (info.stride != info.width * 4) {
        converted.resize(static_cast<size_t>(info.width) * static_cast<size_t>(info.height));
        for (uint32_t y = 0; y < info.height; ++y) {
            const uint32_t* row = reinterpret_cast<const uint32_t*>(
                    static_cast<const uint8_t*>(pixels) + static_cast<size_t>(y) * info.stride);
            std::copy(row, row + info.width, converted.data() + static_cast<size_t>(y) * info.width);
        }
        uploadPixels = converted.data();
    }

    glViewport(0, 0, gRenderer.surfaceWidth, gRenderer.surfaceHeight);
    glClear(GL_COLOR_BUFFER_BIT);
    glUseProgram(gRenderer.program);
    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_2D, gRenderer.texture);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, static_cast<GLsizei>(info.width),
            static_cast<GLsizei>(info.height), 0, uploadFormat, uploadType, uploadPixels);
    GLenum texError = glGetError();
    if (texError != GL_NO_ERROR) {
        AndroidBitmap_unlockPixels(env, bitmap);
        LOGE("render failed: glTexImage2D glError=0x%x bitmap=%ux%u format=%d stride=%u",
                texError, info.width, info.height, info.format, info.stride);
        return false;
    }

    float surfaceAspect = gRenderer.surfaceHeight > 0
            ? static_cast<float>(gRenderer.surfaceWidth) / static_cast<float>(gRenderer.surfaceHeight)
            : 1.0f;
    float imageAspect = static_cast<float>(info.width) / static_cast<float>(info.height);
    float scaleX = 1.0f;
    float scaleY = 1.0f;
    if (imageAspect > surfaceAspect) {
        scaleY = surfaceAspect / imageAspect;
    } else {
        scaleX = imageAspect / surfaceAspect;
    }

    const GLfloat vertices[] = {
            -scaleX, -scaleY, 0.0f, 1.0f,
             scaleX, -scaleY, 1.0f, 1.0f,
            -scaleX,  scaleY, 0.0f, 0.0f,
             scaleX,  scaleY, 1.0f, 0.0f,
    };
    glVertexAttribPointer(static_cast<GLuint>(gRenderer.positionLoc), 2, GL_FLOAT, GL_FALSE,
            4 * sizeof(GLfloat), vertices);
    glEnableVertexAttribArray(static_cast<GLuint>(gRenderer.positionLoc));
    glVertexAttribPointer(static_cast<GLuint>(gRenderer.texCoordLoc), 2, GL_FLOAT, GL_FALSE,
            4 * sizeof(GLfloat), vertices + 2);
    glEnableVertexAttribArray(static_cast<GLuint>(gRenderer.texCoordLoc));
    glUniform1i(gRenderer.samplerLoc, 0);
    glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
    GLenum drawError = glGetError();
    if (drawError != GL_NO_ERROR) {
        AndroidBitmap_unlockPixels(env, bitmap);
        LOGE("render failed: draw glError=0x%x", drawError);
        return false;
    }
    if (eglSwapBuffers(gRenderer.display, gRenderer.surface) != EGL_TRUE) {
        AndroidBitmap_unlockPixels(env, bitmap);
        LOGE("render failed: eglSwapBuffers error=0x%x", eglGetError());
        return false;
    }
    eglMakeCurrent(gRenderer.display, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);

    AndroidBitmap_unlockPixels(env, bitmap);
    return true;
}

}  // namespace

extern "C" JNIEXPORT jboolean JNICALL
Java_com_codex_maanikke_debug_MainActivity_nativeSetPreviewSurface(
        JNIEnv* env, jclass, jobject surface) {
    std::lock_guard<std::mutex> lock(gMutex);
    ANativeWindow* window = surface == nullptr ? nullptr : ANativeWindow_fromSurface(env, surface);
    if (window == nullptr) {
        releaseRendererLocked();
        return JNI_FALSE;
    }
    bool ready = initRendererLocked(window);
    ANativeWindow_release(window);
    return ready ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_codex_maanikke_debug_MainActivity_nativeClearPreviewSurface(JNIEnv*, jclass) {
    std::lock_guard<std::mutex> lock(gMutex);
    releaseRendererLocked();
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_codex_maanikke_debug_MainActivity_nativeRenderPreviewBitmap(
        JNIEnv* env, jclass, jobject bitmap) {
    std::lock_guard<std::mutex> lock(gMutex);
    return renderBitmapLocked(env, bitmap) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_codex_maanikke_debug_NativePreviewRenderer_nativeSetSurface(
        JNIEnv* env, jclass, jobject surface) {
    std::lock_guard<std::mutex> lock(gMutex);
    ANativeWindow* window = surface == nullptr ? nullptr : ANativeWindow_fromSurface(env, surface);
    if (window == nullptr) {
        releaseRendererLocked();
        return JNI_FALSE;
    }
    bool ready = initRendererLocked(window);
    ANativeWindow_release(window);
    return ready ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_codex_maanikke_debug_NativePreviewRenderer_nativeClearSurface(JNIEnv*, jclass) {
    std::lock_guard<std::mutex> lock(gMutex);
    releaseRendererLocked();
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_codex_maanikke_debug_NativePreviewRenderer_nativeRenderBitmap(
        JNIEnv* env, jclass, jobject bitmap) {
    std::lock_guard<std::mutex> lock(gMutex);
    return renderBitmapLocked(env, bitmap) ? JNI_TRUE : JNI_FALSE;
}
