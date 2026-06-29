#include <dlfcn.h>
#include <jni.h>

#include <cstdint>
#include <cstdio>
#include <cstring>
#include <fstream>
#include <sstream>
#include <string>
#include <vector>

using MaaBool = uint8_t;
using MaaSize = uint64_t;
using MaaId = int64_t;
using MaaCtrlId = MaaId;
using MaaResId = MaaId;
using MaaTaskId = MaaId;
using MaaRecoId = MaaId;
using MaaActId = MaaId;
using MaaNodeId = MaaId;
using MaaStatus = int32_t;
using MaaControllerFeature = uint64_t;

static constexpr MaaId kMaaInvalidId = 0;
static constexpr MaaStatus kMaaStatusSucceeded = 3000;

struct MaaStringBuffer;
struct MaaImageBuffer;
struct MaaResource;
struct MaaController;
struct MaaTasker;

struct MaaRect {
    int32_t x;
    int32_t y;
    int32_t width;
    int32_t height;
};

struct MaaCustomControllerCallbacks {
    MaaBool (*connect)(void* trans_arg);
    MaaBool (*connected)(void* trans_arg);
    MaaBool (*request_uuid)(void* trans_arg, MaaStringBuffer* buffer);
    MaaControllerFeature (*get_features)(void* trans_arg);
    MaaBool (*start_app)(const char* intent, void* trans_arg);
    MaaBool (*stop_app)(const char* intent, void* trans_arg);
    MaaBool (*screencap)(void* trans_arg, MaaImageBuffer* buffer);
    MaaBool (*click)(int32_t x, int32_t y, void* trans_arg);
    MaaBool (*swipe)(int32_t x1, int32_t y1, int32_t x2, int32_t y2, int32_t duration, void* trans_arg);
    MaaBool (*touch_down)(int32_t contact, int32_t x, int32_t y, int32_t pressure, void* trans_arg);
    MaaBool (*touch_move)(int32_t contact, int32_t x, int32_t y, int32_t pressure, void* trans_arg);
    MaaBool (*touch_up)(int32_t contact, void* trans_arg);
    MaaBool (*click_key)(int32_t keycode, void* trans_arg);
    MaaBool (*input_text)(const char* text, void* trans_arg);
    MaaBool (*key_down)(int32_t keycode, void* trans_arg);
    MaaBool (*key_up)(int32_t keycode, void* trans_arg);
    MaaBool (*scroll)(int32_t dx, int32_t dy, void* trans_arg);
    MaaBool (*relative_move)(int32_t dx, int32_t dy, void* trans_arg);
    MaaBool (*shell)(const char* cmd, int64_t timeout, void* trans_arg, MaaStringBuffer* buffer);
    MaaBool (*inactive)(void* trans_arg);
    MaaBool (*get_info)(void* trans_arg, MaaStringBuffer* buffer);
};

struct MaaApi {
    void* handle = nullptr;
    const char* (*version)() = nullptr;
    MaaController* (*android_native_controller_create)(const char*) = nullptr;
    void (*controller_destroy)(MaaController*) = nullptr;
    MaaCtrlId (*controller_post_connection)(MaaController*) = nullptr;
    MaaStatus (*controller_wait)(const MaaController*, MaaCtrlId) = nullptr;
    MaaBool (*controller_connected)(const MaaController*) = nullptr;
    MaaBool (*controller_set_option)(MaaController*, int32_t, void*, uint64_t) = nullptr;
    MaaCtrlId (*controller_post_screencap)(MaaController*) = nullptr;
    MaaBool (*controller_cached_image)(const MaaController*, MaaImageBuffer*) = nullptr;
    MaaBool (*controller_get_resolution)(const MaaController*, int32_t*, int32_t*) = nullptr;
    MaaController* (*custom_controller_create)(MaaCustomControllerCallbacks*, void*) = nullptr;
    MaaResource* (*resource_create)() = nullptr;
    void (*resource_destroy)(MaaResource*) = nullptr;
    MaaResId (*resource_post_bundle)(MaaResource*, const char*) = nullptr;
    MaaStatus (*resource_wait)(const MaaResource*, MaaResId) = nullptr;
    MaaBool (*resource_loaded)(const MaaResource*) = nullptr;
    MaaTasker* (*tasker_create)() = nullptr;
    void (*tasker_destroy)(MaaTasker*) = nullptr;
    MaaBool (*tasker_bind_resource)(MaaTasker*, MaaResource*) = nullptr;
    MaaBool (*tasker_bind_controller)(MaaTasker*, MaaController*) = nullptr;
    MaaBool (*tasker_inited)(const MaaTasker*) = nullptr;
    MaaTaskId (*tasker_post_recognition)(MaaTasker*, const char*, const char*, const MaaImageBuffer*) = nullptr;
    MaaStatus (*tasker_wait)(const MaaTasker*, MaaTaskId) = nullptr;
    MaaBool (*tasker_get_task_detail)(
            const MaaTasker*,
            MaaTaskId,
            MaaStringBuffer*,
            MaaNodeId*,
            MaaSize*,
            MaaStatus*) = nullptr;
    MaaBool (*tasker_get_node_detail)(
            const MaaTasker*,
            MaaNodeId,
            MaaStringBuffer*,
            MaaRecoId*,
            MaaActId*,
            MaaBool*) = nullptr;
    MaaBool (*tasker_get_recognition_detail)(
            const MaaTasker*,
            MaaRecoId,
            MaaStringBuffer*,
            MaaStringBuffer*,
            MaaBool*,
            MaaRect*,
            MaaStringBuffer*,
            MaaImageBuffer*,
            void*) = nullptr;
    MaaStringBuffer* (*string_buffer_create)() = nullptr;
    void (*string_buffer_destroy)(MaaStringBuffer*) = nullptr;
    const char* (*string_buffer_get)(const MaaStringBuffer*) = nullptr;
    MaaBool (*string_buffer_set)(MaaStringBuffer*, const char*) = nullptr;
    MaaImageBuffer* (*image_buffer_create)() = nullptr;
    void (*image_buffer_destroy)(MaaImageBuffer*) = nullptr;
    MaaBool (*image_buffer_set_encoded)(MaaImageBuffer*, uint8_t*, MaaSize) = nullptr;
    int32_t (*image_buffer_width)(const MaaImageBuffer*) = nullptr;
    int32_t (*image_buffer_height)(const MaaImageBuffer*) = nullptr;
};

struct FileControllerState {
    const MaaApi* api = nullptr;
    std::string frame_path;
};

static std::string jstring_to_string(JNIEnv* env, jstring value)
{
    if (!value) {
        return {};
    }
    const char* chars = env->GetStringUTFChars(value, nullptr);
    if (!chars) {
        return {};
    }
    std::string result(chars);
    env->ReleaseStringUTFChars(value, chars);
    return result;
}

template <typename T>
static bool load_symbol(void* handle, const char* name, T& out, std::ostringstream& report)
{
    dlerror();
    void* symbol = dlsym(handle, name);
    const char* error = dlerror();
    if (error || !symbol) {
        report << "missingSymbol=" << name << "\n";
        if (error) {
            report << "missingSymbolError=" << error << "\n";
        }
        return false;
    }
    out = reinterpret_cast<T>(symbol);
    return true;
}

static bool load_maa_api(const std::string& framework_path, MaaApi& api, std::ostringstream& report)
{
    api.handle = dlopen(framework_path.c_str(), RTLD_NOW | RTLD_LOCAL);
    if (!api.handle) {
        report << "dlopenError=" << (dlerror() ? dlerror() : "unknown") << "\n";
        return false;
    }

#define LOAD_MAA_SYMBOL(field, symbol) \
    if (!load_symbol(api.handle, symbol, api.field, report)) { return false; }

    LOAD_MAA_SYMBOL(version, "MaaVersion")
    LOAD_MAA_SYMBOL(android_native_controller_create, "MaaAndroidNativeControllerCreate")
    LOAD_MAA_SYMBOL(controller_destroy, "MaaControllerDestroy")
    LOAD_MAA_SYMBOL(controller_post_connection, "MaaControllerPostConnection")
    LOAD_MAA_SYMBOL(controller_wait, "MaaControllerWait")
    LOAD_MAA_SYMBOL(controller_connected, "MaaControllerConnected")
    LOAD_MAA_SYMBOL(controller_set_option, "MaaControllerSetOption")
    LOAD_MAA_SYMBOL(controller_post_screencap, "MaaControllerPostScreencap")
    LOAD_MAA_SYMBOL(controller_cached_image, "MaaControllerCachedImage")
    LOAD_MAA_SYMBOL(controller_get_resolution, "MaaControllerGetResolution")
    LOAD_MAA_SYMBOL(custom_controller_create, "MaaCustomControllerCreate")
    LOAD_MAA_SYMBOL(resource_create, "MaaResourceCreate")
    LOAD_MAA_SYMBOL(resource_destroy, "MaaResourceDestroy")
    LOAD_MAA_SYMBOL(resource_post_bundle, "MaaResourcePostBundle")
    LOAD_MAA_SYMBOL(resource_wait, "MaaResourceWait")
    LOAD_MAA_SYMBOL(resource_loaded, "MaaResourceLoaded")
    LOAD_MAA_SYMBOL(tasker_create, "MaaTaskerCreate")
    LOAD_MAA_SYMBOL(tasker_destroy, "MaaTaskerDestroy")
    LOAD_MAA_SYMBOL(tasker_bind_resource, "MaaTaskerBindResource")
    LOAD_MAA_SYMBOL(tasker_bind_controller, "MaaTaskerBindController")
    LOAD_MAA_SYMBOL(tasker_inited, "MaaTaskerInited")
    LOAD_MAA_SYMBOL(tasker_post_recognition, "MaaTaskerPostRecognition")
    LOAD_MAA_SYMBOL(tasker_wait, "MaaTaskerWait")
    LOAD_MAA_SYMBOL(tasker_get_task_detail, "MaaTaskerGetTaskDetail")
    LOAD_MAA_SYMBOL(tasker_get_node_detail, "MaaTaskerGetNodeDetail")
    LOAD_MAA_SYMBOL(tasker_get_recognition_detail, "MaaTaskerGetRecognitionDetail")
    LOAD_MAA_SYMBOL(string_buffer_create, "MaaStringBufferCreate")
    LOAD_MAA_SYMBOL(string_buffer_destroy, "MaaStringBufferDestroy")
    LOAD_MAA_SYMBOL(string_buffer_get, "MaaStringBufferGet")
    LOAD_MAA_SYMBOL(string_buffer_set, "MaaStringBufferSet")
    LOAD_MAA_SYMBOL(image_buffer_create, "MaaImageBufferCreate")
    LOAD_MAA_SYMBOL(image_buffer_destroy, "MaaImageBufferDestroy")
    LOAD_MAA_SYMBOL(image_buffer_set_encoded, "MaaImageBufferSetEncoded")
    LOAD_MAA_SYMBOL(image_buffer_width, "MaaImageBufferWidth")
    LOAD_MAA_SYMBOL(image_buffer_height, "MaaImageBufferHeight")

#undef LOAD_MAA_SYMBOL

    return true;
}

static const char* buffer_text(const MaaApi& api, MaaStringBuffer* buffer)
{
    if (!buffer || !api.string_buffer_get) {
        return "";
    }
    const char* text = api.string_buffer_get(buffer);
    return text ? text : "";
}

static bool read_file_bytes(const std::string& path, std::vector<uint8_t>& bytes)
{
    std::ifstream input(path, std::ios::binary);
    if (!input) {
        return false;
    }
    input.seekg(0, std::ios::end);
    std::streamoff size = input.tellg();
    if (size <= 0) {
        return false;
    }
    input.seekg(0, std::ios::beg);
    bytes.resize(static_cast<size_t>(size));
    input.read(reinterpret_cast<char*>(bytes.data()), size);
    return input.good();
}

static MaaBool file_controller_connect(void*)
{
    return true;
}

static MaaBool file_controller_connected(void*)
{
    return true;
}

static MaaBool file_controller_request_uuid(void* trans_arg, MaaStringBuffer* buffer)
{
    FileControllerState* state = reinterpret_cast<FileControllerState*>(trans_arg);
    if (!state || !state->api || !buffer) {
        return false;
    }
    return state->api->string_buffer_set(buffer, "maanikke-file-controller") ? true : false;
}

static MaaControllerFeature file_controller_get_features(void*)
{
    return 0;
}

static MaaBool file_controller_screencap(void* trans_arg, MaaImageBuffer* buffer)
{
    FileControllerState* state = reinterpret_cast<FileControllerState*>(trans_arg);
    if (!state || !state->api || !buffer || state->frame_path.empty()) {
        return false;
    }
    std::vector<uint8_t> bytes;
    if (!read_file_bytes(state->frame_path, bytes)) {
        return false;
    }
    return state->api->image_buffer_set_encoded(buffer, bytes.data(), static_cast<MaaSize>(bytes.size())) ? true : false;
}

static MaaBool file_controller_noop_bool(void*)
{
    return true;
}

static MaaBool file_controller_start_stop(const char*, void*)
{
    return true;
}

static MaaBool file_controller_click(int32_t, int32_t, void*)
{
    return true;
}

static MaaBool file_controller_swipe(int32_t, int32_t, int32_t, int32_t, int32_t, void*)
{
    return true;
}

static MaaBool file_controller_touch(int32_t, int32_t, int32_t, int32_t, void*)
{
    return true;
}

static MaaBool file_controller_touch_up(int32_t, void*)
{
    return true;
}

static MaaBool file_controller_key(int32_t, void*)
{
    return true;
}

static MaaBool file_controller_input_text(const char*, void*)
{
    return true;
}

static MaaBool file_controller_scroll(int32_t, int32_t, void*)
{
    return true;
}

static MaaBool file_controller_shell(const char*, int64_t, void* trans_arg, MaaStringBuffer* buffer)
{
    FileControllerState* state = reinterpret_cast<FileControllerState*>(trans_arg);
    if (state && state->api && buffer) {
        state->api->string_buffer_set(buffer, "");
    }
    return true;
}

static MaaBool file_controller_info(void* trans_arg, MaaStringBuffer* buffer)
{
    FileControllerState* state = reinterpret_cast<FileControllerState*>(trans_arg);
    if (!state || !state->api || !buffer) {
        return false;
    }
    std::string info = "{\"type\":\"MaaNikkeFileController\",\"frame_path\":\"" + state->frame_path + "\"}";
    return state->api->string_buffer_set(buffer, info.c_str()) ? true : false;
}

static MaaCustomControllerCallbacks make_file_controller_callbacks()
{
    MaaCustomControllerCallbacks callbacks {};
    callbacks.connect = file_controller_connect;
    callbacks.connected = file_controller_connected;
    callbacks.request_uuid = file_controller_request_uuid;
    callbacks.get_features = file_controller_get_features;
    callbacks.start_app = file_controller_start_stop;
    callbacks.stop_app = file_controller_start_stop;
    callbacks.screencap = file_controller_screencap;
    callbacks.click = file_controller_click;
    callbacks.swipe = file_controller_swipe;
    callbacks.touch_down = file_controller_touch;
    callbacks.touch_move = file_controller_touch;
    callbacks.touch_up = file_controller_touch_up;
    callbacks.click_key = file_controller_key;
    callbacks.input_text = file_controller_input_text;
    callbacks.key_down = file_controller_key;
    callbacks.key_up = file_controller_key;
    callbacks.scroll = file_controller_scroll;
    callbacks.relative_move = file_controller_scroll;
    callbacks.shell = file_controller_shell;
    callbacks.inactive = file_controller_noop_bool;
    callbacks.get_info = file_controller_info;
    return callbacks;
}

static void run_ocr_probe(
        const MaaApi& api,
        MaaResource* resource,
        MaaController* controller,
        MaaImageBuffer* image,
        const std::string& ocr_param_json,
        const char* prefix,
        std::ostringstream& report)
{
    MaaTasker* tasker = nullptr;
    MaaStringBuffer* task_entry = nullptr;
    MaaStringBuffer* node_detail_name = nullptr;
    MaaStringBuffer* node_name = nullptr;
    MaaStringBuffer* algorithm = nullptr;
    MaaStringBuffer* detail = nullptr;

    auto cleanup = [&]() {
        if (detail) api.string_buffer_destroy(detail);
        if (algorithm) api.string_buffer_destroy(algorithm);
        if (node_name) api.string_buffer_destroy(node_name);
        if (node_detail_name) api.string_buffer_destroy(node_detail_name);
        if (task_entry) api.string_buffer_destroy(task_entry);
        if (tasker) api.tasker_destroy(tasker);
    };

    tasker = api.tasker_create();
    report << prefix << "TaskerCreated=" << (tasker ? "true" : "false") << "\n";
    if (tasker && resource) {
        report << prefix << "TaskerBindResource=" << (api.tasker_bind_resource(tasker, resource) ? "true" : "false") << "\n";
        report << prefix << "TaskerBindController=" << (api.tasker_bind_controller(tasker, controller) ? "true" : "false") << "\n";
        report << prefix << "TaskerInited=" << (api.tasker_inited(tasker) ? "true" : "false") << "\n";
    }

    if (!tasker || !image || !api.tasker_inited(tasker)) {
        report << prefix << "OcrSkipped=true\n";
        cleanup();
        return;
    }

    const std::string default_ocr_param =
            "{\"recognition\":\"OCR\","
            "\"roi\":[642,582,193,55],"
            "\"expected\":[\"快速战斗\",\"每周快速战斗\"]}";
    const std::string& reco_param = ocr_param_json.empty() ? default_ocr_param : ocr_param_json;
    report << prefix << "OcrParam=" << reco_param << "\n";
    MaaTaskId task_id = api.tasker_post_recognition(tasker, "OCR", reco_param.c_str(), image);
    MaaStatus reco_status = api.tasker_wait(tasker, task_id);
    report << prefix << "OcrTaskId=" << task_id << "\n";
    report << prefix << "OcrStatus=" << reco_status << "\n";

    MaaRecoId actual_reco_id = kMaaInvalidId;
    MaaNodeId node_ids[16] = {};
    MaaSize node_count = 16;
    MaaStatus task_detail_status = 0;
    task_entry = api.string_buffer_create();
    node_detail_name = api.string_buffer_create();
    bool task_detail_ok = api.tasker_get_task_detail(
            tasker, task_id, task_entry, node_ids, &node_count, &task_detail_status);
    report << prefix << "TaskDetailOk=" << (task_detail_ok ? "true" : "false") << "\n";
    report << prefix << "TaskDetailEntry=" << buffer_text(api, task_entry) << "\n";
    report << prefix << "TaskDetailStatus=" << task_detail_status << "\n";
    report << prefix << "TaskNodeCount=" << node_count << "\n";
    if (task_detail_ok) {
        for (MaaSize i = 0; i < node_count && i < 16; ++i) {
            MaaRecoId node_reco = kMaaInvalidId;
            MaaActId node_action = kMaaInvalidId;
            MaaBool node_completed = false;
            bool node_detail_ok = api.tasker_get_node_detail(
                    tasker, node_ids[i], node_detail_name, &node_reco, &node_action, &node_completed);
            report << prefix << "Node" << i << "DetailOk=" << (node_detail_ok ? "true" : "false") << "\n";
            report << prefix << "Node" << i << "Name=" << buffer_text(api, node_detail_name) << "\n";
            report << prefix << "Node" << i << "RecoId=" << node_reco << "\n";
            report << prefix << "Node" << i << "Completed=" << (node_completed ? "true" : "false") << "\n";
            if (node_detail_ok && node_reco != kMaaInvalidId) {
                actual_reco_id = node_reco;
            }
        }
    }
    if (actual_reco_id == kMaaInvalidId) {
        actual_reco_id = task_id;
        report << prefix << "RecoIdFallbackToTaskId=true\n";
    }
    report << prefix << "RecoId=" << actual_reco_id << "\n";

    node_name = api.string_buffer_create();
    algorithm = api.string_buffer_create();
    detail = api.string_buffer_create();
    MaaBool hit = false;
    MaaRect box { 0, 0, 0, 0 };
    bool detail_ok = actual_reco_id != kMaaInvalidId
            && api.tasker_get_recognition_detail(
                    tasker, actual_reco_id, node_name, algorithm, &hit, &box, detail, nullptr, nullptr);
    report << prefix << "DetailOk=" << (detail_ok ? "true" : "false") << "\n";
    report << prefix << "Hit=" << (hit ? "true" : "false") << "\n";
    report << prefix << "Box=" << box.x << "," << box.y << "," << box.width << "," << box.height << "\n";
    report << prefix << "Node=" << buffer_text(api, node_name) << "\n";
    report << prefix << "Algorithm=" << buffer_text(api, algorithm) << "\n";
    report << prefix << "Detail=" << buffer_text(api, detail) << "\n";
    report << prefix << "Succeeded=" << ((reco_status == kMaaStatusSucceeded && detail_ok) ? "true" : "false") << "\n";
    cleanup();
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_codex_maanikke_rootprobe_MaaCoreNativeBridge_nativeRunProbe(
        JNIEnv* env,
        jclass,
        jint display_id,
        jstring framework_path_j,
        jstring control_unit_path_j,
        jstring resource_path_j,
        jstring frame_path_j,
        jstring ocr_param_json_j)
{
    std::ostringstream report;
    report << "bridgeVersion=maacore_native_probe_p1\n";
    report << "displayId=" << display_id << "\n";

    const std::string framework_path = jstring_to_string(env, framework_path_j);
    const std::string control_unit_path = jstring_to_string(env, control_unit_path_j);
    const std::string resource_path = jstring_to_string(env, resource_path_j);
    const std::string frame_path = jstring_to_string(env, frame_path_j);
    const std::string ocr_param_json = jstring_to_string(env, ocr_param_json_j);
    report << "frameworkPath=" << framework_path << "\n";
    report << "controlUnitPath=" << control_unit_path << "\n";
    report << "resourcePath=" << resource_path << "\n";
    report << "framePath=" << frame_path << "\n";
    report << "ocrParamJson=" << ocr_param_json << "\n";

    MaaApi api;
    bool bridgeLoaded = load_maa_api(framework_path, api, report);
    report << "bridgeLoaded=" << (bridgeLoaded ? "true" : "false") << "\n";
    if (!bridgeLoaded) {
        return env->NewStringUTF(report.str().c_str());
    }
    report << "maaVersion=" << (api.version ? api.version() : "") << "\n";

    MaaResource* resource = api.resource_create();
    report << "resourceCreated=" << (resource ? "true" : "false") << "\n";
    if (resource) {
        MaaResId res_id = api.resource_post_bundle(resource, resource_path.c_str());
        MaaStatus res_status = api.resource_wait(resource, res_id);
        report << "resourceBundleId=" << res_id << "\n";
        report << "resourceBundleStatus=" << res_status << "\n";
        report << "resourceLoaded=" << (api.resource_loaded(resource) ? "true" : "false") << "\n";
    }

    MaaController* android_controller = nullptr;
    MaaImageBuffer* android_image = nullptr;
    std::ostringstream config;
    config << "{"
           << "\"library_path\":\"" << control_unit_path << "\","
           << "\"screen_resolution\":{\"width\":1280,\"height\":720},"
           << "\"display_id\":" << display_id << ","
           << "\"force_stop\":false"
           << "}";
    report << "androidControllerConfig=" << config.str() << "\n";
    android_controller = api.android_native_controller_create(config.str().c_str());
    report << "androidControllerCreated=" << (android_controller ? "true" : "false") << "\n";
    if (android_controller) {
        const bool use_raw = true;
        api.controller_set_option(android_controller, 3, const_cast<bool*>(&use_raw), sizeof(use_raw));
        MaaCtrlId connect_id = api.controller_post_connection(android_controller);
        MaaStatus connect_status = api.controller_wait(android_controller, connect_id);
        report << "androidControllerConnectId=" << connect_id << "\n";
        report << "androidControllerConnectStatus=" << connect_status << "\n";
        report << "androidControllerConnected=" << (api.controller_connected(android_controller) ? "true" : "false") << "\n";
        int32_t raw_width = -1;
        int32_t raw_height = -1;
        report << "androidControllerResolutionReady="
               << (api.controller_get_resolution(android_controller, &raw_width, &raw_height) ? "true" : "false")
               << "\n";
        report << "androidControllerResolution=" << raw_width << "x" << raw_height << "\n";
        MaaCtrlId screencap_id = api.controller_post_screencap(android_controller);
        MaaStatus screencap_status = api.controller_wait(android_controller, screencap_id);
        report << "androidScreencapId=" << screencap_id << "\n";
        report << "androidScreencapStatus=" << screencap_status << "\n";
        android_image = api.image_buffer_create();
        bool cached_image = android_image && api.controller_cached_image(android_controller, android_image);
        report << "androidCachedImage=" << (cached_image ? "true" : "false") << "\n";
        report << "androidCachedImageSize="
               << (android_image ? api.image_buffer_width(android_image) : -1) << "x"
               << (android_image ? api.image_buffer_height(android_image) : -1) << "\n";
        if (cached_image) {
            run_ocr_probe(api, resource, android_controller, android_image, ocr_param_json, "android", report);
        } else {
            report << "androidOcrSkipped=true\n";
        }
    }

    FileControllerState file_state { &api, frame_path };
    MaaCustomControllerCallbacks file_callbacks = make_file_controller_callbacks();
    MaaController* file_controller = api.custom_controller_create(&file_callbacks, &file_state);
    report << "fileControllerCreated=" << (file_controller ? "true" : "false") << "\n";
    MaaImageBuffer* file_image = nullptr;
    if (file_controller) {
        MaaCtrlId connect_id = api.controller_post_connection(file_controller);
        MaaStatus connect_status = api.controller_wait(file_controller, connect_id);
        report << "fileControllerConnectId=" << connect_id << "\n";
        report << "fileControllerConnectStatus=" << connect_status << "\n";
        report << "fileControllerConnected=" << (api.controller_connected(file_controller) ? "true" : "false") << "\n";
        MaaCtrlId screencap_id = api.controller_post_screencap(file_controller);
        MaaStatus screencap_status = api.controller_wait(file_controller, screencap_id);
        report << "fileScreencapId=" << screencap_id << "\n";
        report << "fileScreencapStatus=" << screencap_status << "\n";
        file_image = api.image_buffer_create();
        bool cached_image = file_image && api.controller_cached_image(file_controller, file_image);
        report << "fileCachedImage=" << (cached_image ? "true" : "false") << "\n";
        report << "fileCachedImageSize="
               << (file_image ? api.image_buffer_width(file_image) : -1) << "x"
               << (file_image ? api.image_buffer_height(file_image) : -1) << "\n";
        if (cached_image) {
            run_ocr_probe(api, resource, file_controller, file_image, ocr_param_json, "file", report);
        } else {
            report << "fileOcrSkipped=true\n";
        }
    }

    if (file_image) api.image_buffer_destroy(file_image);
    if (file_controller) api.controller_destroy(file_controller);
    if (android_image) api.image_buffer_destroy(android_image);
    if (android_controller) api.controller_destroy(android_controller);
    if (resource) api.resource_destroy(resource);
    if (api.handle) dlclose(api.handle);
    return env->NewStringUTF(report.str().c_str());
}
