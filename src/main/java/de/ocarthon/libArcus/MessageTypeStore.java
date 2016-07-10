/*
 *   Copyright (C) 2016  Philip Standt
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package de.ocarthon.libArcus;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import com.google.protobuf.Parser;

import java.util.HashMap;
import java.util.Map;

/**
 * This class stores parsers of Protobuf messages and typename if form of a hash to
 * parse such messages later.
 */
class MessageTypeStore {
    private Map<Integer, Parser> parserMap = new HashMap<>();

    /**
     * Package local constructor. Should only be used by {@link ArcusSocket}.
     */
    MessageTypeStore() {
    }

    /**
     * This hash function implements the 32 bit <b>FNV-1a</b> algorithm.
     * <p>
     * <p>The hash has an initial offset of 0x811C9DC5 (2166136261). Then for every byte in
     * the string to be hashed, the hash gets taken xor this byte and then multiplied by
     * 16777619 (32 bit FNV prime).
     *
     * @param in string to be hashed
     * @return hash of this string
     */
    private int hash(String in) {
        int hash = 0x811C9DC5;
        for (int i = 0; i < in.length(); i++) {
            hash ^= (in.charAt(i) & 0xff);
            hash *= 16777619;
        }

        return hash;
    }

    /**
     * Converts the full typename of this the given message into a hash
     *
     * @param message message whose typename gets hashed
     * @return hash of the full typename
     */
    public int getMessageTypeId(Message message) {
        return hash(message.getDescriptorForType().getFullName());
    }

    /**
     * Checks whether a message with the given typename hash has been registered with a
     * call to {@link #registerType(Message)}.
     *
     * @param hash hash of the messages full typename
     * @return whether the message type is registered
     * @see #hasType(String)
     * @see #registerType(Message)
     */
    public boolean hasType(int hash) {
        return parserMap.containsKey(hash);
    }

    /**
     * Checks whether a message with the given typename has been registered with a
     * call to {@link #registerType(Message)}.
     *
     * @param typeName full typename of the message
     * @return whether the message type is registered
     * @see #hasType(int)
     * @see #registerType(Message)
     */
    public boolean hasType(String typeName) {
        return parserMap.containsKey(hash(typeName));
    }

    /**
     * Registers the message to later parse such messages.
     * <p>
     * The given message could theoretically be any instance of a Message, but
     * for readability reasons default instances should be used.
     *
     * @param message message to be registered
     * @return if whe message type has already been registered
     */
    public boolean registerType(Message message) {
        int hash = getMessageTypeId(message);

        if (!parserMap.containsKey(hash)) {
            parserMap.put(hash, message.getParserForType());
            return true;
        }

        return false;
    }

    /**
     * Uses the parser with the corresponding hash to parse the data into
     * an object. The hash can be obtained by calling {@link #getMessageTypeId(Message)}.
     *
     * @param hash hash of the message type
     * @param data data to be parsed
     * @return parsed message
     * @throws InvalidProtocolBufferException if there is a mismatch between hash and data
     * @throws IllegalArgumentException       if the hash of the type is not registered
     */
    public Message parse(int hash, byte[] data) throws InvalidProtocolBufferException {
        Parser parser = parserMap.get(hash);

        if (parser == null) {
            throw new IllegalArgumentException("Unregistered type");
        }

        return (Message) parserMap.get(hash).parseFrom(data);
    }
}