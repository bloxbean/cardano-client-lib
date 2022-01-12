export SRC_LIB_FILE=libcardano_jni_wrapper.so
export TARGET_LIB_FILE=linux-x86-64_libcardano_jni_wrapper.so
export NATIVE_FOLDER=linux-x86-64

cd ../rust
cargo build --all --release
cp target/release/$SRC_LIB_FILE target/release/$TARGET_LIB_FILE
cd ..

mkdir -p native/$NATIVE_FOLDER
cp rust/target/release/$SRC_LIB_FILE native/$NATIVE_FOLDER

RESULT=$?
if [ $RESULT -eq 0 ]; then
  echo success
  ls native/$NATIVE_FOLDER && pwd
else
  exit 1
fi
