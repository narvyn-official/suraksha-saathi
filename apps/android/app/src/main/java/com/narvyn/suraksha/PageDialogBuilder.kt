package com.narvyn.suraksha

import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface

/** Native confirmation behaviour, with explicitly paged menus instead of scrolling item lists. */
internal class PageDialogBuilder(private val host:Context):AlertDialog.Builder(host) {
    private var items:Array<out CharSequence>?=null
    private var selected:DialogInterface.OnClickListener?=null
    private var description:CharSequence?=null
    private var customView:android.view.View?=null
    private var hasNegative=false
    override fun setItems(values:Array<out CharSequence>?,listener:DialogInterface.OnClickListener?):AlertDialog.Builder{items=values;selected=listener;return this}
    override fun setMessage(message:CharSequence?):AlertDialog.Builder{description=message;return super.setMessage(message)}
    override fun setNegativeButton(text:CharSequence?,listener:DialogInterface.OnClickListener?):AlertDialog.Builder{hasNegative=true;return super.setNegativeButton(text,listener)}
    override fun setView(view:android.view.View?):AlertDialog.Builder{customView=view;return super.setView(view)}
    override fun create():AlertDialog {
        val hi=host.getSharedPreferences("preferences",Context.MODE_PRIVATE).getBoolean("hi",false)
        val content=host.column(12)
        val entries=items
        if(entries==null){
            if(description!=null||customView!=null){
                description?.let{content.add(host.label(it.toString(),16f).apply{tag="paged-dialog-message"},bottom=8)}
                if(!(description==null&&customView is PagedPanel))customView?.let{content.add(it)}
                super.setMessage(null)
                super.setView(if(description==null&&customView is PagedPanel)customView else host.paged(content,hi))
            }
            return super.create()
        }
        description?.takeIf{it.isNotBlank()}?.let{content.add(host.label(it.toString(),15f),bottom=12)}
        super.setMessage(null)
        lateinit var dialog:AlertDialog
        entries.forEachIndexed{index,label->content.add(host.action(label.toString(),false,ActionRole.LEARN){dialog.dismiss();selected?.onClick(dialog,index)}.apply{textSize=14f},bottom=8)}
        super.setView(host.paged(content,hi))
        if(!hasNegative)super.setNegativeButton(if(hi)"बंद करें"else"Close",null)
        dialog=super.create();dialog.setOnShowListener{dialog.window?.setLayout(-1,-1)}
        return dialog
    }
}
