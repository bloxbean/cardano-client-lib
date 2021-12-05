#!/usr/bin/env bash
export SRC_LIB_FILE=libcardano_jni_wrapper.so
export TARGET_LIB_FILE=i686-linux-android_libcardano_jni_wrapper.so
export NATIVE_FOLDER=i686-linux-android

cd rust
cargo install cargo-ndk
cargo ndk -t x86 -o ./jniLibs build --release
cp jniLibs/x86/$SRC_LIB_FILE target/release/$TARGET_LIB_FILE
cd ..

mkdir -p native/$NATIVE_FOLDER
cp rust/target/release/$TARGET_LIB_FILE native/$NATIVE_FOLDER

RESULT=$?
if [ $RESULT -eq 0 ]; then
  echo success
  ls native/$NATIVE_FOLDER && pwd
else
  exit 1
fi
