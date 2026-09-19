"use client";
import { useRef, useState, type ComponentProps } from "react";
import { Eye, EyeOff } from "lucide-react";
import { Input } from "@/components/ui/input";
import { Button } from "@/components/ui/button";
export function PasswordInput(props: ComponentProps<typeof Input>) {
  const [visible, setVisible] = useState(false);
  const input = useRef<HTMLInputElement>(null);
  return <div className="password-input"><Input {...props} ref={input} type={visible ? "text" : "password"}
    spellCheck={false} autoCapitalize="none" />
    <Button type="button" variant="ghost" size="icon" disabled={props.disabled}
      aria-label={visible ? "Hide password" : "Show password"} aria-pressed={visible}
      onClick={() => { const start=input.current?.selectionStart,end=input.current?.selectionEnd;setVisible(!visible);
        requestAnimationFrame(()=>{input.current?.focus();if(start!=null&&end!=null)input.current?.setSelectionRange(start,end);}); }}>
      {visible ? <EyeOff size={18}/> : <Eye size={18}/>}</Button></div>;
}
