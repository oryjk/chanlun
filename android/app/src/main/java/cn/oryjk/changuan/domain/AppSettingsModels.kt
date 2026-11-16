package cn.oryjk.changuan.domain

enum class BackendEnvironment(
    val id: String,
    val label: String,
    val shortLabel: String,
    val baseUrl: String,
) {
    DevelopmentProxy(
        id = "development_proxy",
        label = "开发环境（AI外网）",
        shortLabel = "AI外网",
        baseUrl = "https://ai.oryjk.cn:82/chanlun-dev/",
    ),
    Development(
        id = "development",
        label = "开发环境（233内网）",
        shortLabel = "233内网",
        baseUrl = "http://172.16.60.233:8000/",
    ),
    Production(
        id = "production",
        label = "生产环境",
        shortLabel = "生产",
        baseUrl = "https://match.oryjk.cn/chanlun/",
    ),
    ;

    companion object {
        val DEFAULT: BackendEnvironment = DevelopmentProxy

        fun fromId(raw: String?): BackendEnvironment =
            entries.firstOrNull { it.id == raw } ?: DEFAULT
    }
}

enum class AppThemeMode(val label: String) {
    SYSTEM("系统"),
    LIGHT("浅色"),
    DARK("深色"),
    ;

    fun resolveDark(systemInDarkTheme: Boolean): Boolean = when (this) {
        SYSTEM -> systemInDarkTheme
        LIGHT -> false
        DARK -> true
    }

    companion object {
        fun fromRaw(raw: String?): AppThemeMode =
            entries.firstOrNull { it.name == raw } ?: SYSTEM
    }
}
