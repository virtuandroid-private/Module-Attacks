package com.virtualxposed.guestattacker

object Native {
    external fun ptraceOpen(pid: Int, path: String, fileSize: Long): String?
}