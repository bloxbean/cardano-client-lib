export SRC_LIB_FILE=libcardano_jni_wrapper.so
export TARGET_LIB_FILE=linux-armv7_libcardano_jni_wrapper.so
export NATIVE_FOLDER=linux-arm
export RUST_TARGET=armv7-unknown-linux-gnueabihf

cd rust
cargo build --all --release --target $RUST_TARGET
ls -ltr target/$RUST_TARGET/release/*
cp target/$RUST_TARGET/release/$SRC_LIB_FILE target/$RUST_TARGET/release/$TARGET_LIB_FILE
cd ..

mkdir -p native/$NATIVE_FOLDER
cp rust/target/$RUST_TARGET/release/$SRC_LIB_FILE native/$NATIVE_FOLDER

ls native/$NATIVE_FOLDER && pwd
