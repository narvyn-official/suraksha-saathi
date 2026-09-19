package com.narvyn.suraksha

import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom

/** Reach a control through the real Earlier/More controls in the fixed-screen UI.
 * Espresso scrollTo requires ScrollView and no longer describes this app's navigation. */
fun revealOnPage():ViewAction=object:ViewAction {
    override fun getDescription()="show the control using explicit content-page navigation"
    override fun getConstraints()=isAssignableFrom(View::class.java)
    override fun perform(ui:UiController,view:View){
        fun visible():Boolean{val r=Rect();return view.isShown&&view.getGlobalVisibleRect(r)&&r.height()>=view.height*.9&&r.width()>=view.width*.9}
        if(visible())return
        val panels=mutableListOf<PagedPanel>();var parent=view.parent
        while(parent is View){if(parent is PagedPanel)panels.add(parent);parent=parent.parent}
        fun find(v:View,tag:String):View? {if(v.tag==tag)return v;if(v is ViewGroup)for(i in 0 until v.childCount)find(v.getChildAt(i),tag)?.let{return it};return null}
        for(panel in panels.reversed()) {
            repeat(panel.pageCount){if(panel.hasPrevious){check(find(panel,"panel-previous")?.performClick()==true);ui.loopMainThreadUntilIdle()}}
            repeat(panel.pageCount){if(visible())return;val next=find(panel,"panel-next");if(next?.isEnabled==true){check(next.performClick());ui.loopMainThreadUntilIdle()}}
        }
        check(visible()){ "Control is not reachable through the available content pages: ${view.contentDescription ?: view.tag}" }
    }
}
