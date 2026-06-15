package dev.kgoodwin.midnightcouncil.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import dev.kgoodwin.midnightcouncil.voice.VoiceClientTransport;
import org.junit.jupiter.api.Test;

class MidnightCouncilClientTest {

    @Test
    void staleGenerationTransportIsClosedAndNotPublished() {
        MidnightCouncilClient client = new MidnightCouncilClient();
        VoiceClientTransport staleTransport = mock(VoiceClientTransport.class);
        long generation = client.currentVoiceSessionGeneration();

        client.clearActiveVoiceTransport();

        assertFalse(client.publishActiveVoiceTransport(generation, staleTransport));
        verify(staleTransport).close();
        assertNull(client.activeVoiceTransportForTest());
    }

    @Test
    void clearActiveVoiceTransportClosesPublishedTransport() {
        MidnightCouncilClient client = new MidnightCouncilClient();
        VoiceClientTransport activeTransport = mock(VoiceClientTransport.class);

        assertTrue(client.publishActiveVoiceTransport(client.currentVoiceSessionGeneration(), activeTransport));
        assertSame(activeTransport, client.activeVoiceTransportForTest());

        client.clearActiveVoiceTransport();

        verify(activeTransport).close();
        assertNull(client.activeVoiceTransportForTest());
    }
}
