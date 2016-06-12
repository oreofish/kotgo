package com.meibug.tunet

import java.nio.ByteBuffer


/**
 * Created by xing on 16/6/3.
 */
interface Raw {
    fun toRaw(): ByteBuffer;
    fun fromRaw(buffer: ByteBuffer): Raw;
}