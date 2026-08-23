<div align="center">

  # VirtualXposed attack modules

</div>

### Introduction

This repository contains all attack modules used when evaluating the [VirtualXposed](https://github.com/virtuandroid-private/VirtualXposed-private) implementation.
These modules are structured as Legacy Xposed modules written in Kotlin. 
This means that the modules are structured as regular Android apps, with extra metadata to detail how the apps are initialized as Xposed modules.

We use modules in this project to demonstrate malicious behavior which could be embedded within the core virtualization framework.
Instead of embedding the malicious logic within the virtualization framework we use Xposed modules to ensure that the 
virtualization framework can be decoupled from the attacks. This also allows for modularity when deploying multiple attacks.

---

### Building and Installation

Since the modules are Android apps they can be installed within VirtualXposed as normal apps. 
However, once they are installed they need to be enabled within the Xposed Installer app.

To speed up development there are VirtualXposed-specific Gradle tasks which installs the app without any additional confirmation:

```sh
./gradlew network-interceptor:installToXposed
```

**NOTE:** VirtualXposed does not always refresh the modules after installation, it is recommended to force restart the VirtualXposed app after installation.

---

### Modules

There are currently three modules: 

#### Fingerprint spoofer

This module demonstrates how the virtualization framework can be used to spoof the device fingerprint visible to virtualized apps.
It hooks the `android.os.Build` class to override static fields such as `Build.FINGERPRINT`, which is used by apps to fingerprint devices.

#### Network interceptor

This module demonstrates how the virtualization framework can be used to intercept and modify network traffic.
In the demo module we intercept all OkHttp traffic and redirect network calls from the domain example.com to a malicious document.

#### File spoofer

This module demonstrates how the virtualization framework can redirect file access. 
This is accomplished by hooking the File constructor. 
It is not sufficient to hook *all* file usage, but sufficient to demonstrate a proof of concept.
More sophisticated file redirection can be achieved using the existing VirtualXposed [NativeEngine](https://github.com/virtuandroid-private/VirtualXposed-private/blob/master/lib/src/main/java/com/lody/virtual/client/NativeEngine.java).
