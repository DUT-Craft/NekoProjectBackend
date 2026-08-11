package top.foxball.nekomainsite.shared

/** Converts an unexpected missing internal value into an explicit server-side state error. */
fun <T : Any> T?.requirePresent(label: String): T =
    this ?: throw IllegalStateException("$label must be present")
