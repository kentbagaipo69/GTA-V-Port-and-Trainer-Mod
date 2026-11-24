package com.thegwchr.fuzzy;
public final class BuildIntegrity {
 public static boolean valid(android.content.Context c) {
  try {
   android.content.pm.PackageInfo p=c.getPackageManager().getPackageInfo(c.getPackageName(),android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES);
   android.content.pm.Signature[] s=p.signingInfo.getApkContentsSigners();
   if(s.length!=1)return false;
   byte[] h=java.security.MessageDigest.getInstance("SHA-256").digest(s[0].toByteArray());
   StringBuilder b=new StringBuilder();for(byte v:h)b.append(String.format(java.util.Locale.ROOT,"%02x",v&255));
   return (!BuildIdentity.EXPECTED_CERT_SHA256.isEmpty() && b.toString().equals(BuildIdentity.EXPECTED_CERT_SHA256));
  }catch(Exception e){return false;}
 }
}
