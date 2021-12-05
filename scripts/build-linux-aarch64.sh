export SRC_LIB_FILE=libcardano_jni_wrapper.so
export TARGET_LIB_FILE=linux-aarch64_libcardano_jni_wrapper.so
export NATIVE_FOLDER=linux-aarch64
export RUST_TARGET=aarch64-unknown-linux-gnu

cd rust
cargo build --all --release --target $RUST_TARGET
cp target/$RUST_TARGET/release/$SRC_LIB_FILE target/$RUST_TARGET/release/$TARGET_LIB_FILE
cd ..

mkdir -p native/$NATIVE_FOLDER
cp rust/target/$RUST_TARGET/release/$SRC_LIB_FILE native/$NATIVE_FOLDER

RESULT=$?
if [ $RESULT -eq 0 ]; then
  echo success
  ls native/$NATIVE_FOLDER && pwd
else
  exit 1
fi

