"use client";
import { requestJson } from "@/lib/client-api";
import { DialogContent } from "./WorkspaceDialog";
import { Dialog, DialogHeader, DialogTitle, DialogDescription } from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { signOut, AccountActions } from "./AccountActions";
import { useRef, useState, type ReactNode } from "react";
import { LayoutDashboard, Users, CalendarCheck2, ClipboardList, ScanLine, BookOpen, BadgeCheck, QrCode, History, UserRoundCog, Settings2, ShieldCheck, Menu, ChevronDown, LogOut, RefreshCw, MapPin } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Sheet, SheetContent, SheetHeader, SheetTitle, SheetDescription } from "@/components/ui/sheet";
import { DropdownMenu, DropdownMenuTrigger, DropdownMenuContent, DropdownMenuItem, DropdownMenuLabel, DropdownMenuSeparator } from "@/components/ui/dropdown-menu";
import { Select, SelectTrigger, SelectValue, SelectContent, SelectItem } from "@/components/ui/select";
import type { AdminSession } from "./AdminPanel";
export const destinations = [
  {id:"insights",label:"Overview",title:"Training overview",description:"A clear view of learning progress and where your team needs support.",icon:LayoutDashboard,group:"Workspace"},
  {id:"workers",label:"Workers",title:"Your workforce",description:"Manage worker profiles and review individual learning histories.",icon:Users,group:"Workspace"},
  {id:"assignments",label:"Assignments",title:"Training assignments",description:"Plan the next session, track deadlines and follow up on incomplete training.",icon:CalendarCheck2,group:"Workspace"},
  {id:"records",label:"Assessments",title:"Assessment records",description:"Import offline records and review the decisions behind each result.",icon:ClipboardList,group:"Learning"},
  {id:"room-practice",label:"AR practice",title:"AR practice journals",description:"Review virtual actions, retries and requested hints from room training.",icon:ScanLine,group:"Learning"},
  {id:"curriculum",label:"Curriculum",title:"Training curriculum",description:"Explore the five safety domains and their learning objectives.",icon:BookOpen,group:"Learning"},
  {id:"credentials",label:"Credentials",title:"Pilot credentials",description:"Manage signed learning credentials, expiry dates and revocations.",icon:BadgeCheck,group:"Assurance"},
  {id:"verify",label:"Verify a credential",title:"Credential verification",description:"Check a signed QR payload and its current status in this workspace.",icon:QrCode,group:"Assurance"},
  {id:"audit",label:"Activity log",title:"Workspace activity",description:"Follow the changes made by your team, with actor and time recorded.",icon:History,group:"Assurance"},
  {id:"team",label:"Team access",title:"Team & permissions",description:"Give each colleague the access they need to support training.",icon:UserRoundCog,group:"Administration",admin:true},
  {id:"settings",label:"Settings",title:"Centre settings",description:"Manage your training centre’s details and account access.",icon:Settings2,group:"Administration",admin:true},
];
export function WorkspaceShell({session,active,onNavigate,onWorkspace,onRefresh,busy,children}:{session:AdminSession|null;active:string;onNavigate:(id:string)=>void;onWorkspace:(id:string)=>void;onRefresh:()=>void;busy:boolean;children:ReactNode}) {
  const joinPending=useRef(false);
  const [open,setOpen]=useState(false),[join,setJoin]=useState(false),[joinError,setJoinError]=useState(""),[joining,setJoining]=useState(false),[accountOpen,setAccountOpen]=useState(false);
  const page=destinations.find(d=>d.id===active)??destinations[0];
  const links=destinations.filter(d=>!d.admin||session?.current?.role==='admin');
  function go(id:string){onNavigate(id);setOpen(false);}
  const navigation=<nav aria-label="Training centre navigation" className="primary-nav">{["Workspace","Learning","Assurance","Administration"].map(group=>{const items=links.filter(d=>d.group===group);return items.length>0&&<div className="nav-group" key={group}><p className="nav-group-label">{group}</p>{items.map(item=><a key={item.id} href={`#${item.id}`} aria-current={active===item.id?'page':undefined} onClick={e=>{e.preventDefault();go(item.id)}} className={`nav-link ${item.id==='room-practice'?'nav-ar':''}`}><item.icon size={19}/><span>{item.label}</span></a>)}</div>})}</nav>;
  return <div className="app-shell"><Dialog open={accountOpen} onOpenChange={setAccountOpen}><DialogContent><DialogHeader><DialogTitle>Your account</DialogTitle><DialogDescription>Update your password or sign out.</DialogDescription></DialogHeader><AccountActions/></DialogContent></Dialog><Dialog open={join} onOpenChange={setJoin}><DialogContent><DialogHeader><DialogTitle>Join a training centre</DialogTitle><DialogDescription>Enter the private invitation code from your administrator. Use the email they invited.</DialogDescription></DialogHeader><form className="admin-form" onSubmit={async e=>{e.preventDefault();if(joinPending.current)return;joinPending.current=true;setJoining(true);setJoinError('');const invitation=new FormData(e.currentTarget).get('invitation');try{await requestJson('/api/admin/session','POST',{invitation});setJoin(false);onRefresh();}catch(e){setJoinError(e instanceof Error?e.message:'Could not join.')}finally{joinPending.current=false;setJoining(false)}}}><label>Invitation code<Input name="invitation" required maxLength={500} autoComplete="off"/></label>{joinError&&<p role="alert">{joinError}</p>}<Button disabled={joining} type="submit">Join centre</Button></form></DialogContent></Dialog><a href="#workspace-content" className="skip-link">Skip to content</a>
    <aside className="desktop-sidebar"><a className="app-brand" href="#insights" onClick={e=>{e.preventDefault();go('insights')}}><span className="brand-mark"><ShieldCheck size={25}/></span><span>SurakshaAr<small>TRAINING CENTRE</small></span></a>{navigation}<div className="sidebar-note"><ShieldCheck size={18}/><div><strong>Pilot workspace</strong><p>Simulation learning records</p></div></div></aside>
    <div className="app-main"><header className="app-topbar"><div className="topbar-location"><Sheet open={open} onOpenChange={setOpen}><Button className="mobile-menu" variant="ghost" size="icon" aria-label="Open navigation" onClick={()=>setOpen(true)}><Menu size={22}/></Button><SheetContent side="left" className="mobile-nav-sheet"><SheetHeader><SheetTitle>SurakshaAr</SheetTitle><SheetDescription>Training centre navigation</SheetDescription></SheetHeader>{navigation}</SheetContent></Sheet><span className="location-icon"><MapPin size={18}/></span><div><strong>{session?.current?.name??'Training centre'}</strong><span>{session?.current?.site||'Jharkhand · Industrial safety'}</span></div></div>
    <DropdownMenu><DropdownMenuTrigger asChild><button className="account-trigger" aria-label="Open account menu"><span className="avatar">{(session?.user.displayName??'S').slice(0,1).toUpperCase()}</span><span className="account-name">{session?.user.displayName??'Your account'}<small>{session?.current?.role??'Signed in'}</small></span><ChevronDown size={16}/></button></DropdownMenuTrigger><DropdownMenuContent align="end" className="account-dropdown"><DropdownMenuLabel>{session?.user.email??'Account'}</DropdownMenuLabel><DropdownMenuSeparator/><DropdownMenuItem onSelect={()=>setAccountOpen(true)}>Account security</DropdownMenuItem><DropdownMenuItem onSelect={()=>setJoin(true)}>Join a centre</DropdownMenuItem>{session?.current?.role==='admin'&&<DropdownMenuItem onSelect={()=>go('settings')}><Settings2/>Centre settings</DropdownMenuItem>}<DropdownMenuItem onSelect={()=>void signOut().catch(e=>window.alert(e.message))}><LogOut/>Sign out</DropdownMenuItem></DropdownMenuContent></DropdownMenu>
    </header>
    <main id="workspace-content" className="workspace" tabIndex={-1}>
      <div className="workspace-context"><p className="breadcrumb">Training centre <span>/</span> {page.group}</p>{session&&<Select value={session.current?.owner??''} onValueChange={onWorkspace} disabled={busy}><SelectTrigger className="workspace-select" aria-label="Switch workspace"><SelectValue placeholder="Choose workspace"/></SelectTrigger><SelectContent>{session.workspaces.map(w=><SelectItem key={w.owner} value={w.owner}>{w.personal?'My workspace':w.name??'Invited workspace'} · {w.role}</SelectItem>)}</SelectContent></Select>}</div>
      <div className="page-heading"><div><h1 tabIndex={-1} id="page-title">{page.title}</h1><p className="muted">{page.description}</p></div><Button variant="outline" className="refresh-button" aria-label={busy?"Refreshing workspace":"Refresh workspace"} disabled={busy} onClick={onRefresh}><RefreshCw size={16} className={busy?'is-spinning':''}/><span>{busy?'Updating…':'Refresh'}</span></Button></div>
      {children}
      <footer className="scope"><ShieldCheck size={16}/><span>Pilot learning records. Practical competence and permission to work require separate assessment.</span></footer>
    </main></div>
  </div>;
}
