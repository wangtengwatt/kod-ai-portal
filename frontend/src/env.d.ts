/// <reference types="@rsbuild/core/types" />

interface ImportMetaEnv {
  readonly VITE_API_BASE_URL?: string
  readonly VITE_KOD_ANDROID_DOWNLOAD_URL?: string
  readonly VITE_KOD_ANDROID_VERSION?: string
  readonly VITE_KOD_ANDROID_SHA256?: string
  readonly VITE_KOD_ANDROID_FILE_SIZE?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
