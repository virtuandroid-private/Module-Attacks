#include <jni.h>
#include <fcntl.h>
#include <unistd.h>
#include <android/log.h>
#include <cerrno>
#include <cstring>
#include <sys/wait.h>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <sys/ptrace.h>
#include <sys/user.h>
#include <sys/wait.h>
#include <fcntl.h>

#define LOG_TAG "GuestAttacker"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

#define SYSCALL_OPCODE 0x050f // "syscall" opcode in x86_64

extern "C"
JNIEXPORT jstring JNICALL
Java_com_virtualxposed_guestattacker_Native_ptraceOpen(JNIEnv *env, jobject thiz, jint pid, jstring path, jlong file_size) {
    jstring returnValue = nullptr;

#ifdef __x86_64__
    const char *filepath = env->GetStringUTFChars(path, 0);
    LOGI("Ptrace open path %s", filepath);

    struct user_regs_struct orig_regs{}, regs{};
    long orig_code;

    ptrace(PTRACE_ATTACH, pid, NULL, NULL);
    waitpid(pid, NULL, 0);

    // Save original registers and code at current RIP
    ptrace(PTRACE_GETREGS, pid, NULL, &orig_regs);
    memcpy(&regs, &orig_regs, sizeof(regs));
    orig_code = ptrace(PTRACE_PEEKTEXT, pid, orig_regs.rip, NULL);

    // Write path string into stack memory (below RSP to avoid corruption)
    unsigned long path_addr = orig_regs.rsp - 0x100;
    size_t len = strlen(filepath) + 1;
    for (size_t i = 0; i < len; i += sizeof(long)) {
        long data = 0;
        memcpy(&data, filepath + i, (len - i < sizeof(long)) ? (len - i) : sizeof(long));
        ptrace(PTRACE_POKEDATA, pid, path_addr + i, data);
    }

    ptrace(PTRACE_POKETEXT, pid, orig_regs.rip, SYSCALL_OPCODE);

    regs.rax = 2;         // __NR_open (x86_64)
    regs.rdi = path_addr; // const char *filename
    regs.rsi = O_RDONLY;  // int flags
    regs.rdx = 0;         // umode_t mode
    ptrace(PTRACE_SETREGS, pid, NULL, &regs);

    ptrace(PTRACE_SINGLESTEP, pid, NULL, NULL);
    waitpid(pid, NULL, 0);

    ptrace(PTRACE_GETREGS, pid, NULL, &regs);
    LOGI("Injected open() return fd: %lld\n", (long long) regs.rax);

    int target_fd = (int) regs.rax;
    unsigned long target_buf = orig_regs.rsp - 0x200;

    regs.rip = orig_regs.rip;
    regs.rax = 0;          // __NR_read (x86_64)
    regs.rdi = target_fd;  // int fd
    regs.rsi = target_buf; // char *buf
    regs.rdx = file_size; // size_t count
    ptrace(PTRACE_SETREGS, pid, NULL, &regs);

    ptrace(PTRACE_SINGLESTEP, pid, NULL, NULL);
    waitpid(pid, NULL, 0);

    ptrace(PTRACE_GETREGS, pid, NULL, &regs);
    long bytes_read = (long) regs.rax;

    if (bytes_read > 0) {
        char *output_buf = static_cast<char *>(malloc(bytes_read + 1));
        for (size_t i = 0; i < (size_t) bytes_read; i += sizeof(long)) {
            long word = ptrace(PTRACE_PEEKDATA, pid, target_buf + i, NULL);
            memcpy(output_buf + i, &word, (bytes_read - i < sizeof(long)) ? (bytes_read - i) : sizeof(long));
        }
        output_buf[bytes_read] = '\0';

        LOGI("--- Content read from FD %d (%ld bytes) ---\n", target_fd, bytes_read);
        LOGI("%s\n", output_buf);
        LOGI("-------------------------------------------\n");

        returnValue = env->NewStringUTF(output_buf);
        free(output_buf);
    } else {
        LOGI("Failed to read from FD %d. Syscall returned: %ld\n", target_fd, bytes_read);
    }

    // Restore original memory, registers, and detach
    ptrace(PTRACE_POKETEXT, pid, orig_regs.rip, orig_code);
    ptrace(PTRACE_SETREGS, pid, NULL, &orig_regs);
    ptrace(PTRACE_DETACH, pid, NULL, NULL);
#endif
    return returnValue;
}



