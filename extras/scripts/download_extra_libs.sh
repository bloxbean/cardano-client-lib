mkdir -p extras/native/linux-aarch64
mkdir -p extras/native/linux-arm

echo $1

echo "Downloading linux-aarch64"
wget https://github.com/bloxbean/cardano-client-lib/releases/download/$1/linux-aarch64_libcardano_jni_wrapper.so -O extras/native/linux-aarch64/libcardano_jni_wrapper.so

echo "Downloading linux-arm"
wget https://github.com/bloxbean/cardano-client-lib/releases/download/$1/linux-armv7_libcardano_jni_wrapper.so  -O extras/native/linux-arm/libcardano_jni_wrapper.so

