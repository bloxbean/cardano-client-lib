cd rust
cargo build --all --release
copy target\release\cardano_jni_wrapper.dll target\release\windows-x86-64_cardano_jni_wrapper.dll
dir target\release\*
cd ..

mkdir native\win32-x86-64
copy rust\target\release\cardano_jni_wrapper.dll native\win32-x86-64\
dir native\win32-x86-64\*
