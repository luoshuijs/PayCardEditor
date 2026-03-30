# PayCardEditor 

这是一个 Xposed 模块，可以帮助你替换自己的 Mi Pay 卡面为自己想要的图片。

## 原理

本模块采用 Hook 架构，从数据源头和图片加载两个层面实现卡面替换，相比传统的缓存文件直接替换方案更加稳定可靠。

## 感谢

> PayCardEditor 使用了以下开源项目的部分或全部内容，感谢这些项目的开发者们做出的贡献。

| Project Name      | Author/Organization                      | Link                                        |
|-------------------|------------------------------------------|---------------------------------------------|
| Android           | Android Open Source Project, Google Inc. | https://source.android.google.cn/license    |
| AndroidX          | Android Open Source Project, Google Inc. | https://github.com/androidx/androidx        |
| libxposed-service | libxposed                                | https://github.com/libxposed/service        |
| libxposed-api     | libxposed                                | https://github.com/libxposed/api            |
| MiPayCard         | gddhy                                    | https://github.com/gddhy/MiPayCard          |
| HyperCeiler       | ReChronoRain                             | https://github.com/ReChronoRain/HyperCeiler |
| QAuxiliary        | cinit                                    | https://github.com/cinit/QAuxiliary         |
| Ucrop             | yalantis                                 | https://github.com/yalantis/ucrop           |
| Glide             | bumptech                                 | https://github.com/bumptech/glide           |