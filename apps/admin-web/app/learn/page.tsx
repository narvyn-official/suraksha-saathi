import {getAppUser} from '@/lib/auth';
import Login from '@/components/training/Login';
import {LearnerPortal} from '@/components/training/LearnerPortal';
export const dynamic='force-dynamic';
export default async function Page(){return await getAppUser()?<LearnerPortal/>:<Login/>}
