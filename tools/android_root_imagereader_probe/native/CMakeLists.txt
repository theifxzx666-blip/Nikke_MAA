cmake_minimum_required(VERSION 3.22)

project(maanikke_maacore_bridge LANGUAGES CXX)

add_library(maanikke_maacore_bridge SHARED maanikke_maacore_bridge.cpp)

target_compile_features(maanikke_maacore_bridge PRIVATE cxx_std_17)
target_compile_options(maanikke_maacore_bridge PRIVATE -Wall -Wextra -Werror)
target_link_libraries(maanikke_maacore_bridge PRIVATE dl log)
