package ua.pp.odoohost.mentis.data.odoo

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull

// Odoo serializes an empty field as `false`, whatever its type, so every accessor treats
// a boolean primitive as "no value" unless a boolean is asked for.

fun JsonElement.stringOrNull(): String? =
    (this as? JsonPrimitive)?.takeIf { it.isString }?.content

fun JsonElement.intOrNull(): Int? =
    (this as? JsonPrimitive)?.takeUnless { it.isString }?.intOrNull

fun JsonElement.booleanOrFalse(): Boolean =
    (this as? JsonPrimitive)?.booleanOrNull ?: false

/** A many2one value: `[id, "display name"]`, or `false`. */
data class Ref(val id: Int, val name: String)

fun JsonElement.refOrNull(): Ref? {
    val pair = this as? JsonArray ?: return null
    val id = pair.getOrNull(0)?.intOrNull() ?: return null
    return Ref(id, pair.getOrNull(1)?.stringOrNull().orEmpty())
}

fun JsonElement.intList(): List<Int> =
    (this as? JsonArray)?.mapNotNull { it.intOrNull() }.orEmpty()

// Building request payloads.

fun ints(values: Iterable<Int>): JsonArray = JsonArray(values.map(::JsonPrimitive))

fun strings(values: Iterable<String>): JsonArray = JsonArray(values.map(::JsonPrimitive))

/** One domain term: `[field, operator, value]`. */
fun term(field: String, operator: String, value: JsonElement): JsonArray =
    JsonArray(listOf(JsonPrimitive(field), JsonPrimitive(operator), value))

fun term(field: String, operator: String, value: Int?): JsonArray =
    term(field, operator, value?.let(::JsonPrimitive) ?: JsonPrimitive(false))

fun term(field: String, operator: String, value: String): JsonArray = term(field, operator, JsonPrimitive(value))

fun term(field: String, operator: String, value: Boolean): JsonArray = term(field, operator, JsonPrimitive(value))

/** A many2many "replace all" command: `[(6, 0, ids)]`. */
fun setIds(ids: Iterable<Int>): JsonArray = JsonArray(listOf(JsonArray(listOf(JsonPrimitive(6), JsonPrimitive(0), ints(ids)))))
