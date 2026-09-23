import {VerificationForm} from '@/components/training/VerificationForm';
export default async function Page({searchParams}:{searchParams:Promise<{id?:string}>}){const p=await searchParams;return <VerificationForm initialId={typeof p.id==='string'?p.id:''}/>;}
