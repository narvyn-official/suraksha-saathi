'use client';
import {useEffect,useState} from 'react';
import QRCode from 'qrcode';
/** Encodes locally; private invitations and MFA secrets never reach a remote QR service. */
export function LocalQr({value,label}:{value:string;label:string}){
 const [image,setImage]=useState('');
 useEffect(()=>{let current=true;void QRCode.toDataURL(value,{width:224,margin:2,errorCorrectionLevel:'M'}).then(uri=>{if(current)setImage(uri)});return()=>{current=false}},[value]);
 // eslint-disable-next-line @next/next/no-img-element -- generated, private data URI
 return image?<img src={image} width={224} height={224} alt={label} className="local-qr"/>:null;
}
