package com.nostr.band;

import com.google.gson.*
import com.google.gson.annotations.SerializedName
import fr.acinq.secp256k1.Secp256k1
import org.spongycastle.util.encoders.Hex
import java.lang.Exception
import java.lang.reflect.Type
import java.security.MessageDigest
import java.util.*

open class Event(
        val id: ByteArray,
        @SerializedName("pubkey") val pubKey: ByteArray,
        @SerializedName("created_at") val createdAt: Long,
        val kind: Int,
        val tags: List<List<String>>,
        val content: String,
        val sig: ByteArray
) {
    fun toJson(): String = gson.toJson(this)

    /**
     * Checks if the ID is correct and then if the pubKey's secret key signed the event.
     */
    fun checkSignature() {
        if (!id.contentEquals(generateId())) {
            throw Exception(
                    """|Unexpected ID.
                   |  Event: ${toJson()}
                   |  Actual ID: ${id.toHex()}
                   |  Generated: ${generateId().toHex()}""".trimIndent()
            )
        }
        if (!secp256k1.verifySchnorr(sig, id, pubKey)) {
            throw Exception("""Bad signature!""")
        }
    }

    class EventDeserializer : JsonDeserializer<Event> {
        override fun deserialize(
                json: JsonElement,
                typeOfT: Type?,
                context: JsonDeserializationContext?
        ): Event {
            val jsonObject = json.asJsonObject
            return Event(
                    id = Hex.decode(jsonObject.get("id").asString),
                    pubKey = Hex.decode(jsonObject.get("pubkey").asString),
                    createdAt = jsonObject.get("created_at").asLong,
                    kind = jsonObject.get("kind").asInt,
                    tags = jsonObject.get("tags").asJsonArray.map {
                        it.asJsonArray.map { s -> s.asString }
                    },
                    content = jsonObject.get("content").asString,
                    sig = Hex.decode(jsonObject.get("sig").asString)
            )
        }
    }

    class EventSerializer : JsonSerializer<Event> {
        override fun serialize(
                src: Event,
                typeOfSrc: Type?,
                context: JsonSerializationContext?
        ): JsonElement {
            return JsonObject().apply {
                addProperty("id", src.id.toHex())
                addProperty("pubkey", src.pubKey.toHex())
                addProperty("created_at", src.createdAt)
                addProperty("kind", src.kind)
                add("tags", JsonArray().also { jsonTags ->
                    src.tags.forEach { tag ->
                        jsonTags.add(JsonArray().also { jsonTagElement ->
                            tag.forEach { tagElement ->
                                jsonTagElement.add(tagElement)
                            }
                        })
                    }
                })
                addProperty("content", src.content)
                addProperty("sig", src.sig.toHex())
            }
        }
    }

    class ByteArrayDeserializer : JsonDeserializer<ByteArray> {
        override fun deserialize(
                json: JsonElement,
                typeOfT: Type?,
                context: JsonDeserializationContext?
        ): ByteArray = Hex.decode(json.asString)
    }

    class ByteArraySerializer : JsonSerializer<ByteArray> {
        override fun serialize(
                src: ByteArray,
                typeOfSrc: Type?,
                context: JsonSerializationContext?
        ) = JsonPrimitive(src.toHex())
    }

    companion object {
        private val secp256k1 = Secp256k1.get()

        val sha256: MessageDigest = MessageDigest.getInstance("SHA-256")
        val gson: Gson = GsonBuilder()
                .disableHtmlEscaping()
                .registerTypeAdapter(Event::class.java, EventSerializer())
                .registerTypeAdapter(Event::class.java, EventDeserializer())
                .registerTypeAdapter(ByteArray::class.java, ByteArraySerializer())
                .registerTypeAdapter(ByteArray::class.java, ByteArrayDeserializer())
                .create()

        fun generateId(pubKey: ByteArray, createdAt: Long, kind: Int, tags: List<List<String>>, content: String): ByteArray {
            val rawEvent = listOf(
                    0,
                    pubKey.toHex(),
                    createdAt,
                    kind,
                    tags,
                    content
            )
            val rawEventJson = gson.toJson(rawEvent)
            return sha256.digest(rawEventJson.toByteArray())
        }

        fun create(privateKey: ByteArray, kind: Int, tags: List<List<String>> = emptyList(), content: String = "", createdAt: Long = Date().time / 1000): Event {
            val pubKey = Utils.pubkeyCreate(privateKey)
            val id = generateId(pubKey, createdAt, kind, tags, content)
            val sig = Utils.sign(id, privateKey)
            return Event(id, pubKey, createdAt, kind, tags, content, sig)
        }
    }
}

fun Event.generateId(): ByteArray {
    val rawEvent = listOf(
            0,
            pubKey.toHex(),
            createdAt,
            kind,
            tags,
            content
    )
    val rawEventJson = Event.gson.toJson(rawEvent)
    return Event.sha256.digest(rawEventJson.toByteArray())
}