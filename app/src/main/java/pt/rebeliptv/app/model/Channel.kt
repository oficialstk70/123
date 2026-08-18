package pt.rebeliptv.app.model

data class Channel(
    val id: String,
    val name: String,
    val logoUrl: String?,
    val categoryId: String?,
    val categoryName: String,
    val streamUrl: String
)
