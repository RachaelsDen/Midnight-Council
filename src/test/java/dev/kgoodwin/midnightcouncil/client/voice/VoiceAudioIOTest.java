package dev.kgoodwin.midnightcouncil.client.voice;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import dev.kgoodwin.midnightcouncil.voice.VoiceClientService;
import dev.kgoodwin.midnightcouncil.voice.VoiceClientTransport;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class VoiceAudioIOTest {

    @Test
    void bytesToShortsRoundTrip() {
        short[] original = new short[] {0, 1, -1, 100, -100, 32767, -32768, 42};

        byte[] bytes = VoiceAudioIO.shortsToBytes(original);
        short[] roundTrip = VoiceAudioIO.bytesToShorts(bytes);

        assertArrayEquals(original, roundTrip);
    }

    @Test
    void startWithoutAudioDeviceDegradesGracefully() {
        VoiceClientService service = Mockito.mock(VoiceClientService.class);
        VoiceClientTransport transport = Mockito.mock(VoiceClientTransport.class);

        VoiceAudioIO audioIO = new VoiceAudioIO(service, transport);

        assertDoesNotThrow(audioIO::start);

        if (!audioIO.hasMicrophone() && !audioIO.hasSpeaker()) {
            assertFalse(audioIO.hasMicrophone());
            assertFalse(audioIO.hasSpeaker());
        }

        audioIO.close();
    }

    @Test
    void closeIsIdempotent() {
        VoiceClientService service = Mockito.mock(VoiceClientService.class);
        VoiceClientTransport transport = Mockito.mock(VoiceClientTransport.class);

        VoiceAudioIO audioIO = new VoiceAudioIO(service, transport);

        assertDoesNotThrow(audioIO::close);
        assertDoesNotThrow(audioIO::close);
    }

    @Test
    void closeClearsDeviceFlags() {
        VoiceClientService service = Mockito.mock(VoiceClientService.class);
        VoiceClientTransport transport = Mockito.mock(VoiceClientTransport.class);

        VoiceAudioIO audioIO = new VoiceAudioIO(service, transport);

        audioIO.start();
        audioIO.close();

        assertFalse(audioIO.hasMicrophone());
        assertFalse(audioIO.hasSpeaker());
    }
}
