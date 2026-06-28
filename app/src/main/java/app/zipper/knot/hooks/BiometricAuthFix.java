package app.zipper.knot.hooks;

import android.content.pm.Signature;
import android.content.pm.SigningInfo;
import android.os.Build;
import android.util.Base64;
import app.zipper.knot.Knot;
import app.zipper.knot.KnotConfig;
import app.zipper.knot.LoadParam;
import app.zipper.knot.Main;
import io.github.libxposed.api.XposedInterface.Hooker;

public class BiometricAuthFix implements BaseHook {

  private static final String LINE_CERT_B64 =
      "MIICqzCCAhSgAwIBAgIETWsBKjANBgkqhkiG9w0BAQUFADCBmDELMAkGA1UEBhMCSlAxDjAMBgNVBAgTBVRva3lvMRswGQYDVQQHExJPb3Nha2kgU2luYWdhd2Eta3UxEzARBgNVBAoTCk5hdmVySmFwYW4xKTAnBgNVBAsTIFNlYXJjaCBTZXJ2aWNlIERldmVsb3BtZW50IDNUZWFtMRwwGgYDVQQDExN0c3V0b211IGhvcml5YXNoaWtpMCAXDTExMDIyODAxNTgwMloYDzIxMTEwMjA0MDE1ODAyWjCBmDELMAkGA1UEBhMCSlAxDjAMBgNVBAgTBVRva3lvMRswGQYDVQQHExJPb3Nha2kgU2luYWdhd2Eta3UxEzARBgNVBAoTCk5hdmVySmFwYW4xKTAnBgNVBAsTIFNlYXJjaCBTZXJ2aWNlIERldmVsb3BtZW50IDNUZWFtMRwwGgYDVQQDExN0c3V0b211IGhvcml5YXNoaWtpMIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQCNqGsbHHbOnk6ET0AvxQoo5K/FbNhJLZ7kLmfQAupjlChh+gCB4E41Pj9yiPaJd3kpavpBkrSLI42AYu3Gj+68n3gfjI0OoBhGvNlwwxWL1KZUypJwUhfR7Bxam4dtjVkmzGqRM0xejYuXysqVAW2hMDMHkr76s49CxPeESd7wZwIDAQABMA0GCSqGSIb3DQEBBQUAA4GBAC2HdP71+BV8sQm1HDuUSGDaDf51Mmbw8fpfbif+cS94Qj7Xl//zg9byq4VWWl+3rkCPrOcvq4wVdMuN5HghudgQmHiPzFt/Bsrt6863wTskAhlNBDPchtZfhq5wnnAyUSLn6zpzmAE1yNjmUJlLnDSdg4V6w7kbZfBSAA/aYffa";

  private static final Signature ORIGINAL_SIGNATURE =
      new Signature(Base64.decode(LINE_CERT_B64, Base64.DEFAULT));

  private static boolean enabled() {
    return Main.options.fixBiometricAuth.enabled;
  }

  @Override
  public void hook(KnotConfig config, LoadParam lpparam) throws Throwable {
    if (!config.fixBiometricAuth.enabled || Build.VERSION.SDK_INT < 28) return;
    Hooker signers = chain -> enabled() ? new Signature[] {ORIGINAL_SIGNATURE} : chain.proceed();
    hookSigningInfo("getApkContentsSigners", signers);
    hookSigningInfo("getSigningCertificateHistory", signers);
    hookSigningInfo("hasMultipleSigners", chain -> enabled() ? Boolean.FALSE : chain.proceed());
  }

  private void hookSigningInfo(String name, Hooker hooker) {
    try {
      Knot.module.hook(SigningInfo.class.getDeclaredMethod(name)).intercept(hooker);
    } catch (Throwable t) {
      Knot.log("Knot: BiometricAuthFix " + name + " failed: " + t);
    }
  }
}
