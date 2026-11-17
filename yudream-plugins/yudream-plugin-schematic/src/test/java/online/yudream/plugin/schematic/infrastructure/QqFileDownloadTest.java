package online.yudream.plugin.schematic.infrastructure;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class QqFileDownloadTest {
    @Test void acceptsCurrentOfficialGroupFileCdn() {
        assertTrue(QqFileDownload.allowedHost("multimedia.nt.qq.com.cn"));
        assertTrue(QqFileDownload.allowedHost("files.qq.com"));
    }

    @Test void officialCdnExceptionDoesNotAllowLookalikesOrUnrelatedDomains() {
        for (String host : List.of("multimedia.nt.qq.com.cn.evil.test", "evilmultimedia.nt.qq.com.cn",
                "other.qq.com.cn", "multimedia.nt.qq.com.cn.", "127.0.0.1", "localhost")) {
            assertFalse(QqFileDownload.allowedHost(host), host);
        }
    }
}
