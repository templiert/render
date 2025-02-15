package org.janelia.render.client;

import org.janelia.render.client.parameter.CommandLineParameters;
import org.junit.Test;

/**
 * Tests the {@link RenderTilesClient} class.
 *
 * @author Eric Trautman
 */
public class RenderTilesClientTest {

    @Test
    public void testParameterParsing() throws Exception {
        CommandLineParameters.parseHelp(new RenderTilesClient.Parameters());
    }

    // --------------------------------------------------------------
    // The following methods support ad-hoc interactive testing with external render web services.
    // Consequently, they aren't included in the unit test suite.

    public static void main(final String[] args) {
        try {
            final String[] testArgs = {
                    "--baseDataUrl", "http://renderer-dev.int.janelia.org:8080/render-ws/v1",
                    "--owner", "Z0720_07m_VNC",

//                    "--project", "Sec07",
//                    "--stack", "v4_acquire_trimmed_align",
//                    "--rootDirectory", "/nrs/flyem/render/tiles",
//                    "--z", "1817",
//                    "--hackStack", "v4_acquire_trimmed_align_hack",

                    "--project", "Sec19",
                    "--stack", "v7_acquire_trimmed_align_straightened",
                    "--rootDirectory", "/nrs/flyem/render/tiles",
                    "--z", "7547",
                    "--hackStack", "v7_acquire_trimmed_align_straightened_hack",

                    "--completeHackStack"
            };

            RenderTilesClient.main(testArgs);
            
        } catch (final Throwable t) {
            t.printStackTrace();
        }
    }


}
