import ai.mwm.client.data.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import android.app.Application;
import java.io.File;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class)
@Config(sdk=28,manifest=Config.NONE,application=Application.class)
public class TaskOutboxStorageTest {
 private static final String A="account-a:space-one",B="account-b:space-one",C="account-a:space-two";
 private TaskStatusOutbox queue;
 private TaskStatusOutbox fresh(String s){TaskStatusOutbox q=new TaskStatusOutbox(RuntimeEnvironment.getApplication());q.activate(s);return q;}
 @Before public void setup(){File[] files=RuntimeEnvironment.getApplication().getNoBackupFilesDir().listFiles();if(files!=null)for(File f:files)if(f.getName().startsWith("task_status_outbox_"))f.delete();queue=fresh(A);}
 @Test public void persistsAcrossRestart(){queue.enqueue(A,"a",TaskStatus.Done);queue.enqueue(A,"b",TaskStatus.Done);queue.flush(A);assertEquals(2,fresh(A).getUpdates().getValue().getItems().size());assertEquals("a",fresh(A).first(A).getTaskId());}
 @Test public void rapidCheckAndReopenStayOrdered(){queue.enqueue(A,"a",TaskStatus.Done);queue.enqueue(A,"a",TaskStatus.Todo);queue.flush(A);queue.acknowledge(A,queue.first(A).getId());assertEquals(TaskStatus.Todo,fresh(A).first(A).getStatus());}
 @Test public void accountsAndSpacesStaySeparate(){queue.enqueue(A,"a",TaskStatus.Done);queue.flush(A);assertNull(fresh(B).first(B));assertNull(fresh(C).first(C));assertEquals("a",fresh(A).first(A).getTaskId());}
 @Test public void ackKeepsOtherTasks(){queue.enqueue(A,"a",TaskStatus.Done);queue.enqueue(A,"b",TaskStatus.Done);queue.flush(A);queue.acknowledge(A,queue.first(A).getId());assertEquals("b",fresh(A).first(A).getTaskId());}
 @Test public void duplicateTapNotQueuedTwice(){queue.enqueue(A,"a",TaskStatus.Done);queue.enqueue(A,"a",TaskStatus.Done);queue.flush(A);assertEquals(1,fresh(A).getUpdates().getValue().getItems().size());}
 @Test public void ackAfterSwitchKeepsNewAccount(){queue.enqueue(A,"a",TaskStatus.Done);queue.flush(A);String op=queue.first(A).getId();queue.activate(B);queue.enqueue(B,"b",TaskStatus.Done);queue.flush(B);queue.acknowledge(A,op);assertNull(fresh(A).first(A));assertEquals("b",fresh(B).first(B).getTaskId());}
 @Test public void finalAckClearsOutbox(){queue.enqueue(A,"a",TaskStatus.Done);queue.flush(A);queue.acknowledge(A,queue.first(A).getId());assertNull(fresh(A).first(A));}
}
