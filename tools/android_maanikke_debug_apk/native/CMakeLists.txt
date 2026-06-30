cmake_minimum_required(VERSION 3.22)

project(maanikke_preview_renderer LANGUAGES CXX)

add_library(maanikke_preview_renderer SHARED maanikke_preview_renderer.cpp)

target_compile_features(maanikke_preview_renderer PRIVATE cxx_std_17)
target_compile_options(maanikke_preview_renderer PRIVATE -Wall -Wextra -Werror)
target_link_libraries(maanikke_preview_renderer PRIVATE android jnigraphics EGL GLESv2 log)
