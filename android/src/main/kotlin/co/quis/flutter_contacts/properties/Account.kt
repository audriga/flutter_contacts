package co.quis.flutter_contacts.properties

data class Account(
    var rawId: String, // todo this is not nullable. But there is no such thing as a raw account id!
    var type: String,
    var name: String,
    var mimetypes: List<String> = listOf<String>()
) {

    companion object {
        fun fromMap(m: Map<String, Any>): Account = Account(
            m["rawId"] as String,
            m["type"] as String,
            m["name"] as String,
            m["mimetypes"] as List<String>
        )
    }

    fun toMap(): Map<String, Any> = mapOf(
        "rawId" to rawId,
        "type" to type,
        "name" to name,
        "mimetypes" to mimetypes
    )
}
