package org.cortex.terminal.session

internal fun sessionPromptLabel(accountName: String): String =
    "$accountName@localhost ~${if (accountName == "root") '#' else '$'}"
