package com.narvyn.suraksha

import android.content.Context
import android.graphics.Rect
import android.view.*
import android.widget.*
import kotlin.math.min

/** A fixed viewport with explicit pages, never a swipe/scroll container. Keeps fields and actions
 * as their original Views, so changing a page does not lose form input or recreate a GL scene. */
open class PagedPanel(context:Context,private val hindi:Boolean=false,private val showNavigation:Boolean=true):LinearLayout(context) {
    private val originalMediaHeights=java.util.WeakHashMap<View,Int>()
    private val viewport=PageViewport(context)
    private val navigation=LinearLayout(context).apply{gravity=Gravity.CENTER_VERTICAL;setPadding(context.dp(4),context.dp(6),context.dp(4),context.dp(6));background=context.shape(Palette.surface,12)}
    private val previous=context.action(if(hindi)"पिछला भाग"else"Earlier",false,ActionRole.NEUTRAL){previousPage()}.apply{textSize=13f;tag="panel-previous"}
    private val counter=context.label("",12f,Palette.muted).apply{gravity=Gravity.CENTER;tag="panel-page-count";accessibilityLiveRegion=View.ACCESSIBILITY_LIVE_REGION_POLITE}
    private val next=context.action(if(hindi)"और देखें"else"More",false,ActionRole.PRIMARY){nextPage()}.apply{textSize=13f;tag="panel-next"}
    private var stops=listOf(0)
    private var current=0
    private var backCallback:android.window.OnBackInvokedCallback?=null
    private var backDispatcher:android.window.OnBackInvokedDispatcher?=null
    private fun updateBack(){
        if(android.os.Build.VERSION.SDK_INT<33)return
        val wanted=hasPrevious&&isAttachedToWindow&&isShown
        if(wanted&&backCallback==null){val dispatcher=findOnBackInvokedDispatcher()?:return;val callback=android.window.OnBackInvokedCallback{previousPage()};dispatcher.registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,callback);backCallback=callback;backDispatcher=dispatcher}
        else if(!wanted){backCallback?.let{backDispatcher?.unregisterOnBackInvokedCallback(it)};backCallback=null;backDispatcher=null}
    }
    override fun onVisibilityAggregated(visible:Boolean){super.onVisibilityAggregated(visible);updateBack()}
    override fun onDetachedFromWindow(){if(android.os.Build.VERSION.SDK_INT>=33)backCallback?.let{backDispatcher?.unregisterOnBackInvokedCallback(it)};backCallback=null;backDispatcher=null;super.onDetachedFromWindow()}
    var onPageChanged:(()->Unit)?=null
    val hasPrevious get()=current>0
    val pageCount get()=stops.size
    val pageIndex get()=current
    val content get()=viewport.getChildAt(0)
    init{
        orientation=VERTICAL;isBaselineAligned=false
        addView(viewport,LayoutParams(-1,0,1f))
        navigation.addView(previous,LayoutParams(0,-2,1f));navigation.addView(counter,LayoutParams(0,-2,.7f));navigation.addView(next,LayoutParams(0,-2,1f));addView(navigation,LayoutParams(-1,-2))
        navigation.visibility=GONE
    }
    fun setContent(view:View){viewport.removeAllViews();viewport.addView(view,ViewGroup.LayoutParams(-1,-2));current=0;stops=listOf(0);requestLayout()}
    fun firstPage(){select(0)}
    fun previousPage():Boolean {if(!hasPrevious)return false;select(current-1);return true}
    fun nextPage(){if(current<stops.lastIndex)select(current+1)}
    private fun select(index:Int){current=index.coerceIn(0,stops.lastIndex);viewport.scrollTo(0,stops[current]);updateNavigation();requestLayout();onPageChanged?.invoke()}
    private fun updateNavigation(){updateBack();previous.isEnabled=hasPrevious;next.isEnabled=current<stops.lastIndex;counter.text=if(hindi)"भाग ${current+1} / ${stops.size}"else"Part ${current+1} / ${stops.size}";counter.contentDescription=if(hindi)"पृष्ठ ${current+1} / ${stops.size}"else"Page ${current+1} of ${stops.size}"}
    override fun onMeasure(w:Int,h:Int){
        val width=MeasureSpec.getSize(w)
        val available=if(MeasureSpec.getMode(h)==MeasureSpec.UNSPECIFIED)(resources.displayMetrics.heightPixels*.62f).toInt()else MeasureSpec.getSize(h)
        val budget=available.coerceAtLeast(1)
        viewport.measure(MeasureSpec.makeMeasureSpec(width,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(budget,MeasureSpec.EXACTLY))
        val total=content?.measuredHeight?:0
        navigation.visibility=if(showNavigation&&total>budget)VISIBLE else GONE
        if(navigation.visibility==VISIBLE)navigation.measure(MeasureSpec.makeMeasureSpec(width,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(budget,MeasureSpec.AT_MOST))
        val navHeight=if(navigation.visibility==VISIBLE)navigation.measuredHeight else 0
        val height=if(MeasureSpec.getMode(h)==MeasureSpec.EXACTLY)budget else min(budget,total+navHeight)
        viewport.measure(MeasureSpec.makeMeasureSpec(width,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec((height-navHeight).coerceAtLeast(1),MeasureSpec.EXACTLY))
        setMeasuredDimension(width,height)
    }
    override fun onLayout(changed:Boolean,l:Int,t:Int,r:Int,b:Int){
        val navHeight=if(navigation.visibility==VISIBLE)navigation.measuredHeight else 0
        val visibleHeight=(b-t-navHeight).coerceAtLeast(1)
        viewport.layout(0,0,r-l,visibleHeight)
        if(navHeight>0)navigation.layout(0,visibleHeight,r-l,b-t)
        stops=breaks(visibleHeight);current=current.coerceAtMost(stops.lastIndex)
        val end=stops.getOrNull(current+1)?:((content?.height?:0).coerceAtLeast(stops[current]+1))
        viewport.layout(0,0,r-l,min(visibleHeight,end-stops[current]).coerceAtLeast(1))
        viewport.scrollTo(0,stops[current]);updateNavigation();post{onPageChanged?.invoke()}
    }
    private data class Block(val top:Int,val bottom:Int)
    private fun breaks(height:Int):List<Int>{
        val root=content?:return listOf(0);val blocks=mutableListOf<Block>()
        fun collect(v:View,y:Int){
            if(v.visibility==GONE)return
            if(v.height<=height&&v!==root){blocks.add(Block(y,y+v.height));return}
            when{
                v is TextView&&v.layout!=null->{val layout=v.layout;for(i in 0 until layout.lineCount)blocks.add(Block(y+v.totalPaddingTop+layout.getLineTop(i),y+v.totalPaddingTop+layout.getLineBottom(i)))}
                v is ViewGroup->for(i in 0 until v.childCount){val c=v.getChildAt(i);collect(c,y+c.top)}
                else->blocks.add(Block(y,y+v.height))
            }
        }
        collect(root,0);val result=mutableListOf(0);var start=0
        while(start+height<root.height){
            val boundary=start+height
            val crossing=blocks.firstOrNull{it.top<boundary&&it.bottom>boundary}
            var end=if(crossing!=null&&crossing.top>start)crossing.top else boundary
            if(end<=start)end=boundary
            result.add(end);start=end
        }
        return result
    }
    private inner class PageViewport(context:Context):ViewGroup(context){
        init{clipChildren=true;clipToPadding=true;isFocusable=false}
        override fun onMeasure(w:Int,h:Int){
            val width=MeasureSpec.getSize(w);val height=MeasureSpec.getSize(h)
            fun containsScene(v:View):Boolean = v is android.opengl.GLSurfaceView||v is SceneView||v is ComponentSceneView||v is ProcedureSceneView||(v is ViewGroup&&(0 until v.childCount).any{containsScene(v.getChildAt(it))})
            fun fitMedia(v:View){
                val params=v.layoutParams
                if(v!==getChildAt(0)&&params!=null&&params.height>0&&containsScene(v)){
                    val original=originalMediaHeights.getOrPut(v){params.height};params.height=min(original,height)
                }else if(v is ViewGroup)for(i in 0 until v.childCount)fitMedia(v.getChildAt(i))
            }
            getChildAt(0)?.let{fitMedia(it);it.measure(MeasureSpec.makeMeasureSpec(width,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(0,MeasureSpec.UNSPECIFIED))}
            setMeasuredDimension(width,height)
        }
        override fun onLayout(changed:Boolean,l:Int,t:Int,r:Int,b:Int){getChildAt(0)?.let{it.layout(0,0,r-l,it.measuredHeight)}}
        override fun requestChildRectangleOnScreen(child:View,rectangle:Rect,immediate:Boolean):Boolean{
            val target=Rect(rectangle);offsetDescendantRectToMyCoords(child,target)
            val y=target.top+scrollY
            val index=stops.indexOfLast{it<=y}.coerceAtLeast(0)
            if(index!=current){select(index);return true};return false
        }
    }
}

internal fun Context.paged(content:View,hindi:Boolean=false)=PagedPanel(this,hindi).apply{setContent(content)}

internal fun android.app.Activity.popContentPage():Boolean {
    fun walk(v:View):Boolean {
        if(!v.isShown)return false
        if(v is PagedPanel&&v.hasPrevious)return v.previousPage()
        if(v is ViewGroup)for(i in v.childCount-1 downTo 0)if(walk(v.getChildAt(i)))return true
        return false
    }
    return walk(window.decorView)
}
