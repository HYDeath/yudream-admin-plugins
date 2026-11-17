package online.yudream.base.plugin.externalauthqq.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Proxy;
import java.util.*;
import online.yudream.base.plugin.spi.core.PluginContext;
import online.yudream.base.plugin.spi.system.secret.PluginSecretStore;
import online.yudream.base.plugin.spi.system.storage.PluginDocumentStore;
import online.yudream.base.plugin.spi.system.auth.PluginExternalLoginAuthorizeRequest;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** In-memory synthetic app configuration, no QQ network requests. */
class QqConfigTest {
    @Test void configuredProviderUsesFrontendCallbackAndCachesDiscovery() {
        Map<String,Object> stored=new HashMap<>();Map<String,byte[]> secrets=new HashMap<>();int[] reads={0};
        PluginDocumentStore docs=(PluginDocumentStore)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{PluginDocumentStore.class},(p,m,a)->switch(m.getName()){
            case "findById" -> {reads[0]++;yield stored.isEmpty()?Optional.empty():Optional.of(new HashMap<>(stored));}
            case "save" -> {stored.clear();stored.putAll((Map<String,Object>)a[2]);yield stored;}
            default -> throw new UnsupportedOperationException();
        });
        PluginSecretStore store=new PluginSecretStore(){public void put(String k,byte[]b){secrets.put(k,b);}public Optional<byte[]>get(String k){return Optional.ofNullable(secrets.get(k));}public boolean delete(String k){return secrets.remove(k)!=null;}};
        PluginContext context=(PluginContext)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{PluginContext.class},(p,m,a)->switch(m.getName()){case "documents"->docs;case "secrets"->store;default->throw new UnsupportedOperationException();});
        var settings=new SettingsService(context,new ObjectMapper());
        assertFalse(settings.enabled());
        settings.update(Map.of("enabled",true,"appId","123456","appKey","synthetic-config-key","redirectUri","https://test.example/external-login/callback","displayName","QQ 登录","sort",80));
        assertTrue(settings.enabled());assertTrue((Boolean)settings.effective().get("appKeyConfigured"));
        int count=reads[0];settings.enabled();settings.configured();settings.displayName();assertEquals(count,reads[0]);
        try(var provider=new QqExternalLoginProvider(settings,new ObjectMapper())){
            String url=provider.authorizationUrl(new PluginExternalLoginAuthorizeRequest("oauth2","synthetic-state"));
            assertTrue(url.contains("redirect_uri=https%3A%2F%2Ftest.example%2Fexternal-login%2Fcallback"));
            assertTrue(url.contains("state=synthetic-state"));assertFalse(url.contains("synthetic-config-key"));
        }
    }
}
