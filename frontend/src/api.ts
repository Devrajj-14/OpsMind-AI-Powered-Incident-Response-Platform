const BASE=import.meta.env.VITE_API_URL??''
let accessToken=sessionStorage.getItem('opsmind-access')
let refreshToken=localStorage.getItem('opsmind-refresh')

export function setTokens(access:string|null,refresh?:string|null){accessToken=access;if(access)sessionStorage.setItem('opsmind-access',access);else sessionStorage.removeItem('opsmind-access');if(refresh!==undefined){refreshToken=refresh;if(refresh)localStorage.setItem('opsmind-refresh',refresh);else localStorage.removeItem('opsmind-refresh')}}
export const hasSession=()=>Boolean(accessToken||refreshToken)

async function request<T>(path:string,options:RequestInit={},retry=true):Promise<T>{
  const headers=new Headers(options.headers);if(options.body&&!headers.has('Content-Type'))headers.set('Content-Type','application/json');if(accessToken)headers.set('Authorization',`Bearer ${accessToken}`)
  const response=await fetch(`${BASE}/api/v1${path}`,{...options,headers})
  if(response.status===401&&retry&&refreshToken&&!path.startsWith('/auth/')){try{const next=await request<{accessToken:string;refreshToken:string}>('/auth/refresh',{method:'POST',body:JSON.stringify({refreshToken})},false);setTokens(next.accessToken,next.refreshToken);return request<T>(path,options,false)}catch{setTokens(null,null)}}
  if(!response.ok){let message=`Request failed (${response.status})`;try{const problem=await response.json();message=problem.detail??problem.title??message}catch{}throw new Error(message)}
  if(response.status===204)return undefined as T
  return response.json() as Promise<T>
}
export const api={get:<T>(p:string)=>request<T>(p),post:<T>(p:string,b?:unknown,h?:HeadersInit)=>request<T>(p,{method:'POST',body:b===undefined?undefined:JSON.stringify(b),headers:h}),patch:<T>(p:string,b:unknown)=>request<T>(p,{method:'PATCH',body:JSON.stringify(b)})}
