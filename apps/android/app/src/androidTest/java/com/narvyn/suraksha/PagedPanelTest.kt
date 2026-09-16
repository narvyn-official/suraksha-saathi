package com.narvyn.suraksha

import android.graphics.Rect
import android.os.Build
import android.view.*
import android.widget.*
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PagedPanelTest {
    private val ins get()=InstrumentationRegistry.getInstrumentation()
    private fun emulator(){assumeTrue(Build.HARDWARE in listOf("ranchu","goldfish"))}
    private fun all(v:View):List<View> = listOf(v)+(if(v is ViewGroup)(0 until v.childCount).flatMap{all(v.getChildAt(it))}else emptyList())
    @Test fun everyActionHasAWholePageAndPagingRetainsEnteredText(){
        emulator();ActivityScenario.launch(MainActivity::class.java).use{s->
            lateinit var panel:PagedPanel;lateinit var input:EditText
            s.onActivity{a->val body=a.column(12)
                repeat(14){index->body.add(a.action("Action $index"){ }.apply{tag="fixture-$index"},bottom=12)}
                input=EditText(a).apply{setSingleLine();setText("Preserved input");minimumHeight=a.dp(52)};body.add(input)
                panel=a.paged(body);a.setContentView(panel)
            };ins.waitForIdleSync();Thread.sleep(300)
            val seen=mutableSetOf<String>();var count=0
            s.onActivity{count=panel.pageCount;assertTrue(count>1);assertTrue(all(panel).none{it is ScrollView||it is ListView})}
            repeat(count){s.onActivity{
                val viewport=Rect();panel.getChildAt(0).getGlobalVisibleRect(viewport)
                all(panel).filterIsInstance<Button>().filter{it.tag?.toString()?.startsWith("fixture-")==true}.forEach{b->
                    val rect=Rect();if(b.getGlobalVisibleRect(rect)&&rect.height()>0){assertEquals("No half action ${b.tag}",b.height,rect.height());assertTrue(viewport.contains(rect));seen.add(b.tag.toString())}
                }
                panel.nextPage()
            };ins.waitForIdleSync()}
            s.onActivity{assertEquals(14,seen.size);input.setText("Still here");panel.firstPage();while(panel.pageIndex<panel.pageCount-1)panel.nextPage();assertEquals("Still here",input.text.toString())}
        }
    }
    @Test fun aSwipeDoesNotMoveThePage(){
        emulator();ActivityScenario.launch(MainActivity::class.java).use{s->
            lateinit var panel:PagedPanel
            s.onActivity{a->panel=a.paged(a.column().apply{repeat(25){add(a.label("A fixed page item $it",24f),bottom=28)}});a.setContentView(panel)}
            ins.waitForIdleSync();Thread.sleep(250)
            s.onActivity{a->val start=android.os.SystemClock.uptimeMillis();val x=panel.width/2f;val y=panel.height*.75f
                for((time,action,dy)in listOf(Triple(0L,MotionEvent.ACTION_DOWN,0f),Triple(100L,MotionEvent.ACTION_MOVE,-200f),Triple(180L,MotionEvent.ACTION_UP,-300f))){val e=MotionEvent.obtain(start,start+time,action,x,y+dy,0);a.dispatchTouchEvent(e);e.recycle()}
                assertEquals(0,panel.pageIndex);assertEquals(0,panel.getChildAt(0).scrollY)
            }
        }
    }
}
