package io.github.yulimitbreak.aseptic.schema.properties

/**
 * Used to declare lens fields. As lenses don't exist in runtime, we don't even need to
 * store any data in runtime and only use it as a stub and type detection, while code
 * generation uses arguments directly from the constructor method.
 *
 * @see [io.github.yulimitbreak.aseptic.schema.AsepticSchema.lens]
 */
object LensPropertyDeclaration
