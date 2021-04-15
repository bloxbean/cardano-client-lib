mkdir -p native/linux-x86-64
mkdir -p native/darwin-aarch64
mkdir -p native/darwin-x86-64
mkdir -p native/win32-x86-64

echo $1

echo "Downloading linux-x86-64"
wget https://github.com/bloxbean/cardano-client-lib/releases/download/$1/linux-x86-64_libcardano_jni_wrapper.so -O native/linux-x86-64/libcardano_jni_wrapper.so

echo "Downloading darwin-aarch64"
wget https://github.com/bloxbean/cardano-client-lib/releases/download/$1/darwin-aarch64_libcardano_jni_wrapper.dylib  -O native/darwin-aarch64/libcardano_jni_wrapper.dylib

echo "Downloading darwin-x86-64"
wget https://github.com/bloxbean/cardano-client-lib/releases/download/$1/darwin-x86-64_libcardano_jni_wrapper.dylib -O  native/darwin-x86-64/libcardano_jni_wrapper.dylib

echo "Downloading win32-x86-64"
wget https://github.com/bloxbean/cardano-client-lib/releases/download/$1/windows-x86-64_cardano_jni_wrapper.dll -O  native/win32-x86-64/cardano_jni_wrapper.dll

