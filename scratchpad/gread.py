import sys, json, requests
from google.oauth2 import service_account
import google.auth.transport.requests as gar

KEY="/Users/bildno/.config/google-sheets/service-account.json"
SCOPES=["https://www.googleapis.com/auth/spreadsheets.readonly",
        "https://www.googleapis.com/auth/drive.readonly"]
def tok():
    c=service_account.Credentials.from_service_account_file(KEY,scopes=SCOPES)
    c.refresh(gar.Request()); return c.token
H={"Authorization":"Bearer "+tok()}

def tabs(sid):
    r=requests.get(f"https://sheets.googleapis.com/v4/spreadsheets/{sid}",
                   headers=H,params={"fields":"properties.title,sheets.properties"})
    r.raise_for_status(); d=r.json()
    return d["properties"]["title"],[s["properties"]["title"] for s in d["sheets"]]

def values(sid,tab):
    r=requests.get(f"https://sheets.googleapis.com/v4/spreadsheets/{sid}/values/{requests.utils.quote(tab)}",
                   headers=H)
    r.raise_for_status(); return r.json().get("values",[])

def ls(folder):
    out=[];tokn=None
    while True:
        p={"q":f"'{folder}' in parents and trashed=false",
           "fields":"nextPageToken,files(id,name,mimeType,modifiedTime,size)",
           "supportsAllDrives":"true","includeItemsFromAllDrives":"true","pageSize":200,
           "orderBy":"modifiedTime desc"}
        if tokn:p["pageToken"]=tokn
        r=requests.get("https://www.googleapis.com/drive/v3/files",headers=H,params=p)
        r.raise_for_status();d=r.json();out+=d.get("files",[])
        tokn=d.get("nextPageToken")
        if not tokn:break
    return out

def dl(fid,path):
    r=requests.get(f"https://www.googleapis.com/drive/v3/files/{fid}",headers=H,
                   params={"alt":"media","supportsAllDrives":"true"})
    r.raise_for_status(); open(path,"wb").write(r.content); return len(r.content)

if __name__=="__main__":
    cmd=sys.argv[1]
    if cmd=="tabs": print(json.dumps(tabs(sys.argv[2]),ensure_ascii=False))
    elif cmd=="vals":
        for row in values(sys.argv[2],sys.argv[3]): print(row)
    elif cmd=="ls":
        for f in ls(sys.argv[2]):
            print(f"{f['modifiedTime'][:10]}  {f['mimeType'].split('.')[-1][:22]:22s}  {f['name']}  [{f['id']}]")
    elif cmd=="dl": print(dl(sys.argv[2],sys.argv[3]),"bytes")
