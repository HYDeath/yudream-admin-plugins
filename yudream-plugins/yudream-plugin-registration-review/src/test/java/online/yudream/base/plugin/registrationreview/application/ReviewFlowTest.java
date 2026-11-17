package online.yudream.base.plugin.registrationreview.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Proxy;
import java.util.*;
import online.yudream.base.plugin.bilibilitriple.api.BilibiliTripleApi;
import online.yudream.base.plugin.questionbank.api.QuestionBankApi;
import online.yudream.base.plugin.registrationreview.domain.*;
import online.yudream.base.plugin.registrationreview.infrastructure.*;
import online.yudream.base.plugin.spi.core.PluginContext;
import online.yudream.base.plugin.spi.system.storage.PluginDocumentStore;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Synthetic in-process API contracts only, not a real user or upstream service. */
class ReviewFlowTest {
    final Map<String,Map<String,Object>> data = new HashMap<>();
    final PluginDocumentStore docs=(PluginDocumentStore)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{PluginDocumentStore.class},(p,m,a)->switch(m.getName()){
        case "save" -> {var copy=new LinkedHashMap<>((Map<String,Object>)a[2]);copy.put("id",a[1]);data.put(a[0]+":"+a[1],copy);yield copy;}
        case "findById" -> Optional.ofNullable(data.get(a[0]+":"+a[1]));
        case "findAll" -> data.entrySet().stream().filter(e->e.getKey().startsWith(a[0]+":")).map(Map.Entry::getValue).skip(((int)a[1]-1L)*(int)a[2]).limit((int)a[2]).toList();
        default -> throw new UnsupportedOperationException(m.getName());
    });
    boolean finished=false;
    final QuestionBankApi quiz=new QuestionBankApi(){
        public QuestionBankAttempt attempt(String userId,QuestionBankDrawRule rule){return new QuestionBankAttempt("synthetic-session",5,System.currentTimeMillis());}
        public Optional<QuestionBankResult> result(String userId,String sessionId){return Optional.of(new QuestionBankResult(sessionId,finished?"FINISHED":"ONGOING",5,finished?5:0,false,0));}
        public List<QuestionBankCategoryOption> categories(){return List.of();}
    };
    final BilibiliTripleApi bili=new BilibiliTripleApi(){
        public boolean enabled(){return true;}
        public Optional<Evidence> evidence(String userId){long now=System.currentTimeMillis();return Optional.of(new Evidence(userId,"100001","BV1TEST00001",now,now+60000));}
    };
    PluginContext context=(PluginContext)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{PluginContext.class},(p,m,a)->switch(m.getName()){
        case "dependencyAvailable" -> true;
        case "service" -> Optional.of(a[0].equals("questionbank")?quiz:bili);
        default -> throw new UnsupportedOperationException(m.getName());
    });
    ReviewService service(String mode){
        SettingsService settings=new SettingsService(docs,new JsonSupport(new ObjectMapper()));
        settings.update(Map.of("mode",mode,"drawCount",5,"cooldownSeconds",0));
        return new ReviewService(context,new ReviewApplicationRepository(docs),settings);
    }
    @Test void quizWorkflowRetainsOriginalPassingPolicy(){
        var reviews=service("quiz");var started=reviews.start("42","Synthetic User");
        assertEquals(ReviewStatus.QUESTIONNAIRE_ONGOING,started.status());
        finished=true;var passed=reviews.refreshMine("42",started.id());
        assertEquals(ReviewStatus.PASSED,passed.status());assertEquals(80,passed.passingPercent());
        assertEquals(ReviewStatus.APPROVED,reviews.approve(started.id(),"99").status());
    }
    @Test void triplePathStoresServerEvidence(){
        var started=service("triple").start("42","Synthetic User","triple");
        assertEquals(ReviewStatus.PASSED,started.status());assertEquals("BV1TEST00001",started.tripleBvid());
        assertEquals("",started.questionBankSessionId());assertTrue(started.tripleVerifiedAt()>0);
    }
    @Test void combinedModeCreatesQuizAfterRecordingTriple(){
        var started=service("triple_quiz").start("42","Synthetic User");
        assertEquals(ReviewStatus.QUESTIONNAIRE_ONGOING,started.status());
        assertEquals("100001",started.tripleMid());assertEquals("synthetic-session",started.questionBankSessionId());
    }
    @Test void bothModeSupportsEitherPath(){
        var started=service("both").start("42","Synthetic User","triple");
        assertEquals("triple",started.mode());assertEquals(ReviewStatus.PASSED,started.status());
    }
    @Test void evidenceAndPassingSnapshotRoundTrip(){
        var started=service("triple_quiz").start("42","Synthetic User");
        var loaded=ReviewApplication.fromDoc(started.id(),started.toDoc());assertEquals(started,loaded);
    }
    @Test void approvalOutranksALaterOngoingApplication(){
        var reviews=service("quiz");var first=reviews.start("42","Synthetic User");
        finished=true;reviews.refreshMine("42",first.id());reviews.approve(first.id(),"99");
        assertEquals(ReviewStatus.QUESTIONNAIRE_ONGOING,reviews.start("42","Synthetic User").status());
        var admission=reviews.admission("42").orElseThrow();
        assertEquals("approved",admission.status());assertTrue(admission.approved());assertTrue(admission.passed());
    }
}
