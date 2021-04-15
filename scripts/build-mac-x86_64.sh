export SRC_LIB_FILE=libcardano_jni_wrapper.dylib
export TARGET_LIB_FILE=darwin-x86-64_libcardano_jni_wrapper.dylib
export NATIVE_FOLDER=darwin-x86-64

cd rust
cargo build --all --release
cp target/release/$SRC_LIB_FILE target/release/$TARGET_LIB_FILE
cd ..

mkdir -p native/$NATIVE_FOLDER
cp rust/target/release/$SRC_LIB_FILE native/$NATIVE_FOLDER

ls native/$NATIVE_FOLDER && pwd
