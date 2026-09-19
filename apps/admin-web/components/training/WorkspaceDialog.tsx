"use client";
import { useRef, type ComponentProps } from "react";
import { DialogContent as BaseDialogContent } from "@/components/ui/dialog";
/** Workspace dialogs also open from table actions, not only Radix DialogTrigger. */
export function DialogContent(props: ComponentProps<typeof BaseDialogContent>) {
  const opener=useRef<HTMLElement|null>(null);
  return <BaseDialogContent {...props}
    onOpenAutoFocus={event=>{opener.current=document.activeElement instanceof HTMLElement?document.activeElement:null;props.onOpenAutoFocus?.(event);}}
    onCloseAutoFocus={event=>{props.onCloseAutoFocus?.(event);if(event.defaultPrevented)return;event.preventDefault();
      const target=opener.current?.isConnected&&opener.current!==document.body?opener.current:document.getElementById('page-title');target?.focus({preventScroll:true});}}/>;
}
