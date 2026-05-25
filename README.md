# 照片PDF整理

一个单机 Android App，用于把拍照或相册照片合成为 PDF，并对历史 PDF 做分组和排序。

## 功能

- 调用系统相机拍照并生成 PDF
- 从相册一次选择一张或多张图片，合成为一个 PDF
- 历史 PDF 列表
- 自定义分组
- 每个 PDF 可打开、分享、改名、分组、删除
- 长按 PDF 条目拖动排序，也提供上移/下移按钮
- 数据和 PDF 文件保存在 App 本地目录，单机使用

## 云端生成 APK

本项目已配置 GitHub Actions，不需要你本地安装 Android SDK。

1. 在 GitHub 新建一个空仓库。
2. 把本目录 `PhotoPdfOrganizer` 推送到仓库。
3. 打开仓库的 `Actions` 页面。
4. 运行 `Build Android APK` 工作流，或推送到 `main/master` 后自动运行。
5. 构建完成后，在 workflow run 的 `Artifacts` 下载 `PhotoPdfOrganizer-debug-apk`。
6. 解压后得到 `app-debug.apk`，发到手机安装即可。

## 本地目录

项目位置：

`C:\Users\小叮当\PhotoPdfOrganizer`

## 说明

当前输出的是 debug APK，适合自用测试安装。如果后续要正式发布，可以再增加 release 签名配置。
