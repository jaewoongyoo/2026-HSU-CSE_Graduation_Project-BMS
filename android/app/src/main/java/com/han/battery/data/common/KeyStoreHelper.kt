package com.han.battery.data.common

import android.content.Context
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo
import org.bouncycastle.openssl.PEMKeyPair
import org.bouncycastle.openssl.PEMParser
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter
import java.io.InputStream
import java.io.StringReader
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.CertificateFactory

object KeyStoreHelper {

    fun getKeyStore(
        context: Context,
        certResId: Int,
        keyResId: Int,
        caResId: Int
    ): KeyStore {
        val certFactory = CertificateFactory.getInstance("X.509")

        // 1. CA 인증서 로드
        val caInput: InputStream = context.resources.openRawResource(caResId)
        val caCert = certFactory.generateCertificate(caInput)
        caInput.close()

        // 2. 디바이스 인증서 로드
        val certInput: InputStream = context.resources.openRawResource(certResId)
        val deviceCert = certFactory.generateCertificate(certInput)
        certInput.close()

        // 3. 개인키 로드 (BouncyCastle 사용)
        val keyInput: InputStream = context.resources.openRawResource(keyResId)
        val keyString = keyInput.bufferedReader().use { it.readText() }
        keyInput.close()

        val privateKey = parsePrivateKey(keyString)

        // 4. PKCS12 타입으로 KeyStore 생성 (BKS 절대 아님!)
        val keyStore = KeyStore.getInstance("PKCS12")
        keyStore.load(null, null)

        // 5. 빈 비밀번호로 저장
        keyStore.setCertificateEntry("ca-certificate", caCert)
        val emptyPw = "".toCharArray()
        keyStore.setKeyEntry("default", privateKey, emptyPw, arrayOf(deviceCert))

        return keyStore
    }

    private fun parsePrivateKey(pemString: String): PrivateKey {
        val pemParser = PEMParser(StringReader(pemString))
        val pemObject = pemParser.readObject()
        pemParser.close()

        val converter = JcaPEMKeyConverter()

        // AWS에서 주는 키 형식에 맞게 처리
        return when (pemObject) {
            is PEMKeyPair -> converter.getKeyPair(pemObject).private
            is PrivateKeyInfo -> converter.getPrivateKey(pemObject)
            else -> throw IllegalArgumentException("지원하지 않는 키 형식: ${pemObject?.javaClass?.name}")
        }
    }
}