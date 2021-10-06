#!/usr/bin/env bash
export SRC_LIB_FILE=libcardano_jni_wrapper.so
export TARGET_LIB_FILE=aarch64-linux-android_libcardano_jni_wrapper.so
export NATIVE_FOLDER=aarch64-linux-android

cd rust
cargo install cargo-ndk
cargo ndk -t arm64-v8a -o ./jniLibs build --release
cp jniLibs/arm64-v8a/$SRC_LIB_FILE target/release/$TARGET_LIB_FILE
cd ..

mkdir -p native/$NATIVE_FOLDER
cp rust/target/release/$TARGET_LIB_FILE native/$NATIVE_FOLDER

ls native/$NATIVE_FOLDER && pwd
