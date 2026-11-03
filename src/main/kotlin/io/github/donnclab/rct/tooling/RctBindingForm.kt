package io.github.donnclab.rct.tooling

import io.github.donnclab.rct.DefaultRctParser
import io.github.donnclab.rct.RctBindingPart
import io.github.donnclab.rct.RctTextNode

public data class RctSourceFilter(public val name: String, public val args: List<String> = emptyList())
public data class RctSourceBinding(public val path: String, public val filters: List<RctSourceFilter> = emptyList())

/** Core-backed editing of a single binding. Composite text remains a source text property. */
public object RctBindingForm {
    public fun read(value: String): RctSourceBinding? = try {
        val node = DefaultRctParser().parseFragment("left \"$value\"").singleOrNull() as? RctTextNode
        val part = node?.text?.parts?.singleOrNull() as? RctBindingPart
        part?.let { RctSourceBinding(it.path, it.filters.map { filter -> RctSourceFilter(filter.name, filter.args) }) }
    } catch (_: IllegalArgumentException) { null }

    public fun write(binding: RctSourceBinding): String {
        require(binding.path.isNotBlank()) { "Choose a data field." }
        val value = "{{" + binding.path + binding.filters.joinToString("") { filter ->
            " | " + filter.name + if (filter.args.isEmpty()) "" else ":" + filter.args.joinToString(",")
        } + "}}"
        require(read(value) == binding) { "Invalid binding or formatter arguments." }
        return value
    }
}
