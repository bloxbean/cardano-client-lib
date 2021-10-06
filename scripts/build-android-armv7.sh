#!/usr/bin/env bash
export SRC_LIB_FILE=libcardano_jni_wrapper.so
export TARGET_LIB_FILE=armv7-linux-androideabi_libcardano_jni_wrapper.so
export NATIVE_FOLDER=armv7-linux-androideabi

cd rust
cargo install cargo-ndk
cargo ndk -t armeabi-v7a -o ./jniLibs build --release
cp jniLibs/armeabi-v7a/$SRC_LIB_FILE target/release/$TARGET_LIB_FILE
cd ..

mkdir -p native/$NATIVE_FOLDER
cp rust/target/release/$TARGET_LIB_FILE native/$NATIVE_FOLDER

ls native/$NATIVE_FOLDER && pwd
