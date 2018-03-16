/*
 * Copyright © 2017 Schlichtherle IT Services
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package global.namespace.fun.io.jackson;

import com.fasterxml.jackson.databind.ObjectMapper;
import global.namespace.fun.io.api.Codec;
import global.namespace.fun.io.api.Decoder;
import global.namespace.fun.io.api.Encoder;
import global.namespace.fun.io.api.Socket;

import java.io.InputStream;
import java.io.OutputStream;

final class JSONCodec implements Codec {

    private final ObjectMapper mapper;

    JSONCodec(final ObjectMapper m) { this.mapper = m; }

    @Override
    public Encoder encoder(Socket<OutputStream> output) { return obj -> output.accept(out -> mapper.writeValue(out, obj)); }

    @Override
    public Decoder decoder(final Socket<InputStream> input) {
        return new Decoder() {
            @Override
            public <T> T decode(Class<T> expected) throws Exception {
                return input.apply(in -> mapper.readValue(in, expected));
            }
        };
    }
}
