/***** BEGIN LICENSE BLOCK *****
 * Version: CPL 1.0/GPL 2.0/LGPL 2.1
 *
 * The contents of this file are subject to the Common Public
 * License Version 1.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of
 * the License at http://www.eclipse.org/legal/cpl-v10.html
 *
 * Software distributed under the License is distributed on an "AS
 * IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * rights and limitations under the License.
 *
 * Copyright (C) 2008 Ola Bini <ola.bini@gmail.com>
 * 
 * Alternatively, the contents of this file may be used under the terms of
 * either of the GNU General Public License Version 2 or later (the "GPL"),
 * or the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
 * in which case the provisions of the GPL or the LGPL are applicable instead
 * of those above. If you wish to allow use of your version of this file only
 * under the terms of either the GPL or the LGPL, and not to allow others to
 * use your version of this file under the terms of the CPL, indicate your
 * decision by deleting the provisions above and replace them with the notice
 * and other provisions required by the GPL or the LGPL. If you do not delete
 * the provisions above, a recipient may use your version of this file under
 * the terms of any one of the CPL, the GPL or the LGPL.
 ***** END LICENSE BLOCK *****/
package org.jruby.ext.openssl.impl;

import javax.crypto.Cipher;
import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.ASN1EncodableVector;
import org.bouncycastle.asn1.ASN1OctetString;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.DEREncodable;
import org.bouncycastle.asn1.DERObjectIdentifier;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.asn1.DERTaggedObject;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;

/** PKCS7_ENC_CONTENT
 *
 * @author <a href="mailto:ola.bini@gmail.com">Ola Bini</a>
 */
public class EncContent {
    /**
     * Describe contentType here.
     */
    private int contentType;

    /**
     * Describe cipher here.
     */
    private Cipher cipher;

    /**
     * Describe algorithm here.
     */
    private AlgorithmIdentifier algorithm;

    /**
     * Describe encData here.
     */
    private ASN1OctetString encData;

    /**
     * Get the <code>ContentType</code> value.
     *
     * @return an <code>int</code> value
     */
    public final int getContentType() {
        return contentType;
    }

    /**
     * Set the <code>ContentType</code> value.
     *
     * @param newContentType The new ContentType value.
     */
    public final void setContentType(final int newContentType) {
        this.contentType = newContentType;
    }

    /**
     * Get the <code>Cipher</code> value.
     *
     * @return a <code>Cipher</code> value
     */
    public final Cipher getCipher() {
        return cipher;
    }

    /**
     * Set the <code>Cipher</code> value.
     *
     * @param newCipher The new Cipher value.
     */
    public final void setCipher(final Cipher newCipher) {
        this.cipher = newCipher;
    }

    /**
     * Get the <code>Algorithm</code> value.
     *
     * @return an <code>AlgorithmIdentifier</code> value
     */
    public final AlgorithmIdentifier getAlgorithm() {
        return algorithm;
    }

    /**
     * Set the <code>Algorithm</code> value.
     *
     * @param newAlgorithm The new Algorithm value.
     */
    public final void setAlgorithm(final AlgorithmIdentifier newAlgorithm) {
        this.algorithm = newAlgorithm;
    }

    /**
     * Get the <code>EncData</code> value.
     *
     * @return an <code>ASN1OctetString</code> value
     */
    public final ASN1OctetString getEncData() {
        return encData;
    }

    /**
     * Set the <code>EncData</code> value.
     *
     * @param newEncData The new EncData value.
     */
    public final void setEncData(final ASN1OctetString newEncData) {
        this.encData = newEncData;
    }

    @Override
    public String toString() {
        return "#<EncContent contentType="+contentType+" algorithm="+(algorithm == null ? "null" : ASN1Registry.o2a(algorithm.getObjectId()))+" content="+encData+">";
    }

    /**
     * EncryptedContentInfo ::= SEQUENCE {
     *   contentType ContentType,
     *   contentEncryptionAlgorithm ContentEncryptionAlgorithmIdentifier,
     *   encryptedContent [0] IMPLICIT EncryptedContent OPTIONAL }
     *
     * EncryptedContent ::= OCTET STRING
     */
    public static EncContent fromASN1(DEREncodable content) {
        ASN1Sequence sequence = (ASN1Sequence)content;
        DERObjectIdentifier contentType = (DERObjectIdentifier)(sequence.getObjectAt(0));
        int nid = ASN1Registry.obj2nid(contentType);

        EncContent ec = new EncContent();
        ec.setContentType(nid);
        ec.setAlgorithm(AlgorithmIdentifier.getInstance(sequence.getObjectAt(1)));
        if(sequence.size() > 2 && sequence.getObjectAt(2) instanceof DERTaggedObject && ((DERTaggedObject)(sequence.getObjectAt(2))).getTagNo() == 0) {
            DEREncodable ee = ((DERTaggedObject)(sequence.getObjectAt(2))).getObject();
            if(ee instanceof ASN1Sequence) {
            } else {
                ec.setEncData((ASN1OctetString)ee);
            }
        }
        return ec;
    }

    public ASN1Encodable asASN1() {
        ASN1EncodableVector vector = new ASN1EncodableVector();
        vector.add(ASN1Registry.nid2obj(contentType).toASN1Object());
        vector.add(algorithm.toASN1Object());
        if(encData != null) {
            vector.add(new DERTaggedObject(0, encData).toASN1Object());
        }
        return new DERSequence(vector);
   }
}// EncContent
