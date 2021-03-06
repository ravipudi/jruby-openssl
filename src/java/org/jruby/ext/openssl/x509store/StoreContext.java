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
 * Copyright (C) 2006 Ola Bini <ola@ologix.com>
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
package org.jruby.ext.openssl.x509store;

import java.security.PublicKey;
import java.security.cert.X509CRL;
import java.security.cert.X509Certificate;
import java.security.cert.X509Extension;

import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.HashSet;

import org.bouncycastle.asn1.ASN1InputStream;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.asn1.DERInteger;

import org.jruby.ext.openssl.OpenSSLReal;

/**
 * c: X509_STORE_CTX
 *
 * @author <a href="mailto:ola.bini@ki.se">Ola Bini</a>
 */
public class StoreContext {
    public Store ctx;
    public int currentMethod;

    public X509AuxCertificate certificate;
    public List<X509AuxCertificate> untrusted;
    public List<X509CRL> crls;

    public VerifyParameter param;

    public List<X509AuxCertificate> otherContext;

    public static interface CheckPolicyFunction extends Function1 {
        public static final CheckPolicyFunction EMPTY = new CheckPolicyFunction(){
                public int call(Object arg0) {
                    return -1;
                }
            };
    }

    public Store.VerifyFunction verify;
    public Store.VerifyCallbackFunction verifyCallback;
    public Store.GetIssuerFunction getIssuer;
    public Store.CheckIssuedFunction checkIssued;
    public Store.CheckRevocationFunction checkRevocation;
    public Store.GetCRLFunction getCRL;
    public Store.CheckCRLFunction checkCRL;
    public Store.CertificateCRLFunction certificateCRL;
    public CheckPolicyFunction checkPolicy;
    public Store.CleanupFunction cleanup;

    public boolean isValid;
    public int lastUntrusted;
    
    public List<X509AuxCertificate> chain; //List<X509AuxCertificate>
    public PolicyTree tree;

    public int explicitPolicy;

    public int errorDepth;
    public int error;
    public X509AuxCertificate currentCertificate;
    public X509AuxCertificate currentIssuer;
    public java.security.cert.CRL currentCRL;

    public List<Object> extraData;

    /**
     * c: X509_STORE_CTX_set_depth
     */
    public void setDepth(int depth) { 
        param.setDepth(depth);
    }

    /**
     * c: X509_STORE_CTX_set_app_data
     */
    public void setApplicationData(Object data) {
        setExtraData(0,data);
    }

    /**
     * c: X509_STORE_CTX_get_app_data
     */
    public Object getApplicationData() {
        return getExtraData(0);
    }

    /**
     * c: X509_STORE_CTX_get1_issuer
     */
    public int getFirstIssuer(X509AuxCertificate[] issuer, X509AuxCertificate x) throws Exception { 
        Name xn = new Name(x.getIssuerX500Principal());
        X509Object[] s_obj = new X509Object[1];
        int ok = ctx == null ? 0 : getBySubject(X509Utils.X509_LU_X509,xn,s_obj);
        if(ok != X509Utils.X509_LU_X509) {
            if(ok == X509Utils.X509_LU_RETRY) {
                X509Error.addError(X509Utils.X509_R_SHOULD_RETRY);
                return -1;
            } else if (ok != X509Utils.X509_LU_FAIL) {
                return -1;
            }
            return 0;
        }
        X509Object obj = s_obj[0];
        if(this.checkIssued.call(this,x,((Certificate)obj).x509) != 0) {
            issuer[0] = ((Certificate)obj).x509;
            return 1;
        }

        int idx = X509Object.indexBySubject(ctx.objs,X509Utils.X509_LU_X509, xn);
        if(idx == -1) {
            return 0;
        }

        /* Look through all matching certificates for a suitable issuer */
        for(int i = idx; i < ctx.objs.size(); i++) {
            X509Object pobj = (X509Object)ctx.objs.get(i);
            if(pobj.type() != X509Utils.X509_LU_X509) {
                return 0;
            }
            if(!xn.isEqual((((Certificate)pobj).x509).getSubjectX500Principal())) {
                return 0;
            }
            if(this.checkIssued.call(this,x,((Certificate)pobj).x509) != 0) {
                issuer[0] = ((Certificate)pobj).x509;
                return 1;
            }
        }
        return 0;
    }

    public static List<X509AuxCertificate> ensureAux(Collection inp) {
        List<X509AuxCertificate> out = new ArrayList<X509AuxCertificate>();
        for(Object o : inp) {
            out.add(ensureAux((X509Certificate)o));
        }
        return out;
    }

    public static List<X509AuxCertificate> ensureAux(X509Certificate[] inp) {
        List<X509AuxCertificate> o = new ArrayList<X509AuxCertificate>();
        for(X509Certificate c : inp) {
            o.add(ensureAux(c));
        }
        return o;
    }

    public static X509AuxCertificate ensureAux(X509Certificate i) {
        if(i instanceof X509AuxCertificate) {
            return (X509AuxCertificate)i;
        } else {
            return new X509AuxCertificate(i);
        }
    }

    /**
     * c: X509_STORE_CTX_init
     */
    public int init(Store store, X509AuxCertificate x509, List<X509AuxCertificate> chain) { 
        int ret = 1;
        this.ctx=store;
        this.currentMethod=0;
        this.certificate=x509;
        this.untrusted=ensureAux(chain);
        this.crls = null;
        this.lastUntrusted=0;
        this.otherContext = null;
        this.isValid=false;
        this.chain = null;
        this.error=0;
        this.explicitPolicy=0;
        this.errorDepth=0;
        this.currentCertificate=null;
        this.currentIssuer=null;
        this.tree = null;

        this.param = new VerifyParameter();

        if(store != null) {
            ret = param.inherit(store.param);
        } else {
            param.flags |= X509Utils.X509_VP_FLAG_DEFAULT | X509Utils.X509_VP_FLAG_ONCE;
        }
        if(store != null) {
            verifyCallback = store.verifyCallback;
            cleanup = store.cleanup;
        } else {
            cleanup = Store.CleanupFunction.EMPTY;
        }

        if(ret != 0) {
            ret = param.inherit(VerifyParameter.lookup("default"));
        }

        if(ret == 0) {
            X509Error.addError(X509Utils.ERR_R_MALLOC_FAILURE);
            return 0;
        }

        if(store != null && store.checkIssued != null && store.checkIssued != Store.CheckIssuedFunction.EMPTY) {
            this.checkIssued = store.checkIssued;
        } else {
            this.checkIssued = defaultCheckIssued;
        }

        if(store != null && store.getIssuer != null && store.getIssuer != Store.GetIssuerFunction.EMPTY) {
            this.getIssuer = store.getIssuer;
        } else {
            this.getIssuer = new Store.GetIssuerFunction() {
                    public int call(Object arg1, Object arg2, Object arg3) throws Exception {
                        return ((StoreContext)arg2).getFirstIssuer((X509AuxCertificate[])arg1,(X509AuxCertificate)arg3);
                    }
                };
        }

        if(store != null && store.verifyCallback != null && store.verifyCallback != Store.VerifyCallbackFunction.EMPTY) {
            this.verifyCallback = store.verifyCallback;
        } else {
            this.verifyCallback = NullCallback;
        }

        if(store != null && store.verify != null && store.verify != Store.VerifyFunction.EMPTY) {
            this.verify = store.verify;
        } else {
            this.verify = internalVerify;
        }

        if(store != null && store.checkRevocation != null && store.checkRevocation != Store.CheckRevocationFunction.EMPTY) {
            this.checkRevocation = store.checkRevocation;
        } else {
            this.checkRevocation = defaultCheckRevocation;
        }

        if(store != null && store.getCRL != null && store.getCRL != Store.GetCRLFunction.EMPTY) {
            this.getCRL = store.getCRL;
        } else {
            this.getCRL = defaultGetCRL;
        }

        if(store != null && store.checkCRL != null && store.checkCRL != Store.CheckCRLFunction.EMPTY) {
            this.checkCRL = store.checkCRL;
        } else {
            this.checkCRL = defaultCheckCRL;
        }

        if(store != null && store.certificateCRL != null && store.certificateCRL != Store.CertificateCRLFunction.EMPTY) {
            this.certificateCRL = store.certificateCRL;
        } else {
            this.certificateCRL = defaultCertificateCRL;
        }

        this.checkPolicy = defaultCheckPolicy;

        this.extraData = new ArrayList<Object>();
        this.extraData.add(null);this.extraData.add(null);this.extraData.add(null);
        this.extraData.add(null);this.extraData.add(null);this.extraData.add(null);
        return 1;
    } 

    /**
     * c: X509_STORE_CTX_trusted_stack
     */
    public void trustedStack(List<X509AuxCertificate> sk) {
        otherContext = sk;
        getIssuer = getIssuerStack;
    }

    /**
     * c: X509_STORE_CTX_cleanup
     */
    public void cleanup() throws Exception {
        if(cleanup != null && cleanup != Store.CleanupFunction.EMPTY) {
            cleanup.call(this);
        }
        param = null;
        tree = null;
        chain = null;
        extraData = null;
    } 

    /**
     * c: find_issuer
     */
    public X509AuxCertificate findIssuer(List<X509AuxCertificate> sk, X509AuxCertificate x) throws Exception {
        for(X509AuxCertificate issuer : sk) {
            if(checkIssued.call(this,x,issuer) != 0) {
                return issuer;
            }
        }
        return null;
    }

    /**
     * c: X509_STORE_CTX_set_ex_data
     */
    public int setExtraData(int idx,Object data) { 
        extraData.set(idx,data);
        return 1; 
    } 

    /**
     * c: X509_STORE_CTX_get_ex_data
     */
    public Object getExtraData(int idx) { 
        return extraData.get(idx); 
    }

    /**
     * c: X509_STORE_CTX_get_error
     */
    public int getError() { 
        return error;
    }

    /**
     * c: X509_STORE_CTX_set_error
     */
    public void setError(int s) {
        this.error = s;
    } 

    /**
     * c: X509_STORE_CTX_get_error_depth
     */
    public int getErrorDepth() { 
        return errorDepth; 
    } 

    /**
     * c: X509_STORE_CTX_get_current_cert
     */
    public X509AuxCertificate getCurrentCertificate() { 
        return currentCertificate; 
    }

    /**
     * c: X509_STORE_CTX_get_chain
     */
    public List<X509AuxCertificate> getChain() { 
        return chain; 
    } 

    /**
     * c: X509_STORE_CTX_get1_chain
     */
    public List getFirstChain() { 
        if(null == chain) {
            return null;
        }
        return new ArrayList<X509AuxCertificate>(chain); 
    } 

    /**
     * c: X509_STORE_CTX_set_cert
     */
    public void setCertificate(X509AuxCertificate x) {
        this.certificate = x;
    } 

    /**
     * c: X509_STORE_CTX_set_chain
     */
    public void setChain(List sk) {
        this.untrusted = ensureAux(sk);
    } 

    /**
     * c: X509_STORE_CTX_set0_crls
     */
    public void setCRLs(List<X509CRL> sk) {
        this.crls = sk;
    } 

    /**
     * c: X509_STORE_CTX_set_purpose
     */
    public int setPurpose(int purpose) { 
        return purposeInherit(0,purpose,0);
    }

    /**
     * c: X509_STORE_CTX_set_trust
     */
    public int setTrust(int trust) { 
        return purposeInherit(0,0,trust);
    }

    private void resetSettingsToWithoutStore() {
        ctx = null;
        this.param = new VerifyParameter();
        this.param.flags |= X509Utils.X509_VP_FLAG_DEFAULT | X509Utils.X509_VP_FLAG_ONCE;
        this.param.inherit(VerifyParameter.lookup("default"));
        this.cleanup = Store.CleanupFunction.EMPTY;
        this.checkIssued = defaultCheckIssued;
        this.getIssuer = new Store.GetIssuerFunction() {
                public int call(Object arg1, Object arg2, Object arg3) throws Exception {
                    return ((StoreContext)arg2).getFirstIssuer((X509AuxCertificate[])arg1,(X509AuxCertificate)arg3);
                }
            };
        this.verifyCallback = NullCallback;
        this.verify = internalVerify;
        this.checkRevocation = defaultCheckRevocation;
        this.getCRL = defaultGetCRL;
        this.checkCRL = defaultCheckCRL;
        this.certificateCRL = defaultCertificateCRL;
    }

    /**
     * c: SSL_CTX_load_verify_locations
     */
    public int loadVerifyLocations(String CAfile, String CApath) {
        boolean reset = false;
        try {
            if(ctx == null) {
                reset = true;
                ctx = new Store();
                this.param.inherit(ctx.param);
                param.inherit(VerifyParameter.lookup("default"));
                this.verifyCallback = ctx.verifyCallback;
                this.cleanup = ctx.cleanup;
                if(ctx.checkIssued != null && ctx.checkIssued != Store.CheckIssuedFunction.EMPTY) {
                    this.checkIssued = ctx.checkIssued;
                }
                if(ctx.getIssuer != null && ctx.getIssuer != Store.GetIssuerFunction.EMPTY) {
                    this.getIssuer = ctx.getIssuer;
                }

                if(ctx.verifyCallback != null && ctx.verifyCallback != Store.VerifyCallbackFunction.EMPTY) {
                    this.verifyCallback = ctx.verifyCallback;
                }

                if(ctx.verify != null && ctx.verify != Store.VerifyFunction.EMPTY) {
                    this.verify = ctx.verify;
                }

                if(ctx.checkRevocation != null && ctx.checkRevocation != Store.CheckRevocationFunction.EMPTY) {
                    this.checkRevocation = ctx.checkRevocation;
                }

                if(ctx.getCRL != null && ctx.getCRL != Store.GetCRLFunction.EMPTY) {
                    this.getCRL = ctx.getCRL;
                }

                if(ctx.checkCRL != null && ctx.checkCRL != Store.CheckCRLFunction.EMPTY) {
                    this.checkCRL = ctx.checkCRL;
                }

                if(ctx.certificateCRL != null && ctx.certificateCRL != Store.CertificateCRLFunction.EMPTY) {
                    this.certificateCRL = ctx.certificateCRL;
                }
            }

            int ret = ctx.loadLocations(CAfile, CApath);
            if(ret == 0 && reset) resetSettingsToWithoutStore();

            return ret;
        } catch(Exception e) {
            if(reset) {
                resetSettingsToWithoutStore();
            }
            return 0;
        }
    }

    /**
     * c: X509_STORE_CTX_purpose_inherit
     */
    public int purposeInherit(int defaultPurpose,int purpose, int trust) { 
        int idx;
        if(purpose == 0) {
            purpose = defaultPurpose;
        }
        if(purpose != 0) {
            idx = Purpose.getByID(purpose);
            if(idx == -1) {
                X509Error.addError(X509Utils.X509_R_UNKNOWN_PURPOSE_ID);
                return 0;
            }
            Purpose ptmp = Purpose.getFirst(idx);
            if(ptmp.trust == X509Utils.X509_TRUST_DEFAULT) {
                idx = Purpose.getByID(defaultPurpose);
                if(idx == -1) {
                    X509Error.addError(X509Utils.X509_R_UNKNOWN_PURPOSE_ID);
                    return 0;
                }
                ptmp = Purpose.getFirst(idx);
            }
            if(trust == 0) {
                trust = ptmp.trust;
            }
        }
        if(trust != 0) {
            idx = Trust.getByID(trust);
            if(idx == -1) {
                X509Error.addError(X509Utils.X509_R_UNKNOWN_TRUST_ID);
                return 0;
            }
        }

        if(purpose != 0 && param.purpose == 0) {
            param.purpose = purpose;
        }
        if(trust != 0 && param.trust == 0) {
            param.trust = trust;
        }
        return 1;
    } 

    /**
     * c: X509_STORE_CTX_set_flags
     */
    public void setFlags(long flags) {
        param.setFlags(flags);
    } 

    /**
     * c: X509_STORE_CTX_set_time
     */
    public void setTime(long flags,Date t) {
        param.setTime(t);
    } 

    /**
     * c: X509_STORE_CTX_set_verify_cb
     */
    public void setVerifyCallback(Store.VerifyCallbackFunction verifyCallback) {
        this.verifyCallback = verifyCallback;
    } 

    /**
     * c: X509_STORE_CTX_get0_policy_tree
     */
    PolicyTree getPolicyTree() {
        return tree;
    }

    /**
     * c: X509_STORE_CTX_get_explicit_policy
     */
    public int getExplicitPolicy() { 
        return explicitPolicy;
    } 

    /**
     * c: X509_STORE_CTX_get0_param
     */
    public VerifyParameter getParam() { 
        return param; 
    } 

    /**
     * c: X509_STORE_CTX_set0_param
     */
    public void setParam(VerifyParameter param) {
        this.param = param;
    } 

    /**
     * c: X509_STORE_CTX_set_default
     */
    public int setDefault(String name) { 
        VerifyParameter p = VerifyParameter.lookup(name);
        if(p == null) {
            return 0;
        }
        return param.inherit(p);
    }

    /**
     * c: X509_STORE_get_by_subject
     */
    public int getBySubject(int type,Name name,X509Object[] ret) throws Exception { 
        Store c = ctx;

        X509Object tmp = X509Object.retrieveBySubject(c.objs,type,name);
        if(tmp == null) {
            for(int i=currentMethod; i<c.certificateMethods.size(); i++) {
                Lookup lu = (Lookup)c.certificateMethods.get(i);
                X509Object[] stmp = new X509Object[1];
                int j = lu.bySubject(type,name,stmp);
                if(j<0) {
                    currentMethod = i;
                    return j;
                } else if(j>0) {
                    tmp = stmp[0];
                    break;
                }
            }
            currentMethod = 0;
            if(tmp == null) {
                return 0;
            }
        }
        ret[0] = tmp;
        return 1;
    }

    /**
     * c: X509_verify_cert
     */
    public int verifyCertificate() throws Exception {
        X509AuxCertificate x,xtmp=null,chain_ss = null;
        //X509_NAME xn;
        int bad_chain = 0;
        int depth,i,ok=0;
        int num;
        Store.VerifyCallbackFunction cb;
        List<X509AuxCertificate> sktmp = null;
        if(certificate == null) {
            X509Error.addError(X509Utils.X509_R_NO_CERT_SET_FOR_US_TO_VERIFY);
            return -1;
        }
        cb=verifyCallback;

        if(null == chain) {
            chain = new ArrayList<X509AuxCertificate>();
            chain.add(certificate);
            lastUntrusted = 1;
        }

        if(untrusted != null) {
            sktmp = new ArrayList<X509AuxCertificate>(untrusted);
        }
        num = chain.size();
        x = chain.get(num-1);
        depth = param.depth;
        for(;;) {
            if(depth < num) {
                break;
            }

            if(checkIssued.call(this,x,x) != 0) {
                break;
            }

            if(untrusted != null) {
                xtmp = findIssuer(sktmp,x);
                if(xtmp != null) {
                    chain.add(xtmp);
                    sktmp.remove(xtmp);
                    lastUntrusted++;
                    x = xtmp;
                    num++;
                    continue;
                }
            }
            break;
        }

        i = chain.size();
        x = (X509AuxCertificate)chain.get(i-1);

        if(checkIssued.call(this,x,x) != 0) {
            if(chain.size() == 1) {
                X509AuxCertificate[] p_xtmp = new X509AuxCertificate[]{xtmp};
                ok = getIssuer.call(p_xtmp,this,x);
                xtmp = p_xtmp[0];
                if(ok <= 0 || !x.equals(xtmp)) {
                    error = X509Utils.V_ERR_DEPTH_ZERO_SELF_SIGNED_CERT;
                    currentCertificate = x;
                    errorDepth = i-1;
                    bad_chain = 1;
                    ok = cb.call(new Integer(0),this);
                    if(ok == 0) {
                        return ok;
                    }
                } else {
                    x = xtmp;
                    chain.set(i-1,x);
                    lastUntrusted = 0;
                }
            } else {
                chain_ss = chain.remove(chain.size()-1);
                lastUntrusted--;
                num--;
                x = chain.get(num-1);
            }
        }
        for(;;) {
            if(depth<num) {
                break;
            }
            //xn = new X509_NAME(x.getIssuerX500Principal());
            if(checkIssued.call(this,x,x) != 0) {
                break;
            }
            X509AuxCertificate[] p_xtmp = new X509AuxCertificate[]{xtmp};
            ok = getIssuer.call(p_xtmp,this,x);
            xtmp = p_xtmp[0];
            if(ok < 0) {
                return ok;
            }
            if(ok == 0) {
                break;
            }
            x = xtmp;
            chain.add(x);
            num++;
        }

        //xn = new X509_NAME(x.getIssuerX500Principal());
        if(checkIssued.call(this,x,x) == 0) {
            if(chain_ss == null || checkIssued.call(this,x,chain_ss) == 0) {
                if(lastUntrusted >= num) {
                    error = X509Utils.V_ERR_UNABLE_TO_GET_ISSUER_CERT_LOCALLY;
                } else {
                    error = X509Utils.V_ERR_UNABLE_TO_GET_ISSUER_CERT;
                }
                currentCertificate = x;
            } else {
                chain.add(chain_ss);
                num++;
                lastUntrusted = num;
                currentCertificate = chain_ss;
                error = X509Utils.V_ERR_SELF_SIGNED_CERT_IN_CHAIN;
                chain_ss = null;
            }
            errorDepth = num-1;
            bad_chain = 1;
            ok = cb.call(new Integer(0),this);
            if(ok == 0) {
                return ok;
            }
        }

        ok = checkChainExtensions();
        if(ok == 0) {
            return ok;
        }

        if(param.trust > 0) {
            ok = checkTrust();
        }
        if(ok == 0) {
            return ok;
        }

        ok = checkRevocation.call(this);
        if(ok == 0) {
            return ok;
        }

        if(verify != null && verify != Store.VerifyFunction.EMPTY) {
            ok = verify.call(this);
        } else {
            ok = internalVerify.call(this);
        }
        if(ok == 0) {
            return ok;
        }

        if(bad_chain == 0 && (param.flags & X509Utils.V_FLAG_POLICY_CHECK) != 0) {
            ok = checkPolicy.call(this);
        }
        return ok;
    }


    private final static Set<String> CRITICAL_EXTENSIONS = new HashSet<String>();
    static {
        CRITICAL_EXTENSIONS.add("2.16.840.1.113730.1.1"); // netscape cert type, NID 71
        CRITICAL_EXTENSIONS.add("2.5.29.15"); // key usage, NID 83
        CRITICAL_EXTENSIONS.add("2.5.29.17"); // subject alt name, NID 85
        CRITICAL_EXTENSIONS.add("2.5.29.19"); // basic constraints, NID 87
        CRITICAL_EXTENSIONS.add("2.5.29.37"); // ext key usage, NID 126
        CRITICAL_EXTENSIONS.add("1.3.6.1.5.5.7.1.14"); // proxy cert info, NID 661
    }

    private static boolean supportsCriticalExtension(String oid) {
        return CRITICAL_EXTENSIONS.contains(oid);
    }

    private static boolean unhandledCritical(X509Extension xx) {
        if(xx.getCriticalExtensionOIDs() == null || xx.getCriticalExtensionOIDs().size() == 0) {
            return false;
        }
        for(String ss : xx.getCriticalExtensionOIDs()) {
            if(!supportsCriticalExtension(ss)) {
                return true;
            }
        }
        return false;
    }

    /**
     * c: check_chain_extensions
     */
    public int checkChainExtensions() throws Exception {
        int ok=0, must_be_ca;
        X509AuxCertificate x;
        Store.VerifyCallbackFunction cb;
        int proxy_path_length = 0;
        int allow_proxy_certs = (param.flags & X509Utils.V_FLAG_ALLOW_PROXY_CERTS) != 0 ? 1 : 0;
        cb = verifyCallback;
        must_be_ca = -1;

        try {
            if (System.getenv("OPENSSL_ALLOW_PROXY_CERTS") != null && !"false".equalsIgnoreCase((String) System.getenv("OPENSSL_ALLOW_PROXY_CERTS"))) {
                allow_proxy_certs = 1;
            }
        } catch (Error e) {
            // just ignore if we can't use System.getenv
        }

        for(int i = 0; i<lastUntrusted;i++) {
            int ret;
            x = chain.get(i);
            if((param.flags & X509Utils.V_FLAG_IGNORE_CRITICAL) == 0 && unhandledCritical(x)) {
                error = X509Utils.V_ERR_UNHANDLED_CRITICAL_EXTENSION;
                errorDepth = i;
                currentCertificate = x;
                ok = cb.call(new Integer(0),this);
                if(ok == 0) {
                    return ok;
                }
            }
            if(allow_proxy_certs == 0 && x.getExtensionValue("1.3.6.1.5.5.7.1.14") != null) {
                error = X509Utils.V_ERR_PROXY_CERTIFICATES_NOT_ALLOWED;
                errorDepth = i;
                currentCertificate = x;
                ok = cb.call(new Integer(0),this);
                if(ok == 0) {
                    return ok;
                }
            }

            ret = Purpose.checkCA(x);
            switch(must_be_ca) {
            case -1:
                if((param.flags & X509Utils.V_FLAG_X509_STRICT) != 0 && ret != 1 && ret != 0) {
                    ret = 0;
                    error = X509Utils.V_ERR_INVALID_CA;
                } else {
                    ret = 1;
                }
                break;
            case 0:
                if(ret != 0) {
                    ret = 0;
                    error = X509Utils.V_ERR_INVALID_NON_CA;
                } else {
                    ret = 1;
                }
                break;
            default:
                if(ret == 0 || ((param.flags & X509Utils.V_FLAG_X509_STRICT) != 0 && ret != 1)) {
                    ret = 0;
                    error = X509Utils.V_ERR_INVALID_CA;
                } else {
                    ret = 1;
                }
                break;
            }
            if(ret == 0) {
                errorDepth = i;
                currentCertificate = x;
                ok = cb.call(new Integer(0),this);
                if(ok == 0) {
                    return ok;
                }
            }
            if(param.purpose > 0) {
                ret = Purpose.checkPurpose(x,param.purpose, must_be_ca > 0 ? 1 : 0);
                if(ret == 0 || ((param.flags & X509Utils.V_FLAG_X509_STRICT) != 0 && ret != 1)) {
                    error = X509Utils.V_ERR_INVALID_PURPOSE;
                    errorDepth = i;
                    currentCertificate = x;
                    ok = cb.call(new Integer(0),this);
                    if(ok == 0) {
                        return ok;
                    }
                }
            }

            if(i > 1 && x.getBasicConstraints() != -1 && (i > (x.getBasicConstraints() + proxy_path_length + 1))) {
                error = X509Utils.V_ERR_PATH_LENGTH_EXCEEDED;
                errorDepth = i;
                currentCertificate = x;
                ok = cb.call(new Integer(0),this);
                if(ok == 0) {
                    return ok;
                }
            }

            if(x.getExtensionValue("1.3.6.1.5.5.7.1.14") != null) {
                DERSequence pci = (DERSequence)new ASN1InputStream(x.getExtensionValue("1.3.6.1.5.5.7.1.14")).readObject();
                if(pci.size() > 0 && pci.getObjectAt(0) instanceof DERInteger) {
                    int pcpathlen = ((DERInteger)pci.getObjectAt(0)).getValue().intValue();
                    if(i > pcpathlen) {
                        error = X509Utils.V_ERR_PROXY_PATH_LENGTH_EXCEEDED;
                        errorDepth = i;
                        currentCertificate = x;
                        ok = cb.call(new Integer(0),this);
                        if(ok == 0) {
                            return ok;
                        }
                    }
                }
                proxy_path_length++;
                must_be_ca = 0;
            } else {
                must_be_ca = 1;
            }
        }
        return 1;
    }

    /**
     * c: X509_check_trust
     */
    public int checkTrust() throws Exception {
        int i,ok;
        X509AuxCertificate x;
        Store.VerifyCallbackFunction cb;
        cb = verifyCallback;
        i = chain.size()-1;
        x = chain.get(i);
        ok = Trust.checkTrust(x,param.trust,0);
        if(ok == X509Utils.X509_TRUST_TRUSTED) {
            return 1;
        }
        errorDepth = 1;
        currentCertificate = x;
        if(ok == X509Utils.X509_TRUST_REJECTED) {
            error = X509Utils.V_ERR_CERT_REJECTED;
        } else {
            error = X509Utils.V_ERR_CERT_UNTRUSTED;
        }
        return cb.call(new Integer(0),this);
    }

    /**
     * c: check_cert_time
     */
    public int checkCertificateTime(X509AuxCertificate x) throws Exception {
        Date ptime = null;

        if((param.flags & X509Utils.V_FLAG_USE_CHECK_TIME) != 0) {
            ptime = this.param.checkTime;
        } else {
            ptime = Calendar.getInstance().getTime();
        }
        if(!x.getNotBefore().before(ptime)) {
            error = X509Utils.V_ERR_CERT_NOT_YET_VALID;
            currentCertificate = x;
            if(verifyCallback.call(new Integer(0),this) == 0) {
                return 0;
            }
        }
        if(!x.getNotAfter().after(ptime)) {
            error = X509Utils.V_ERR_CERT_HAS_EXPIRED;
            currentCertificate = x;
            if(verifyCallback.call(new Integer(0),this) == 0) {
                return 0;
            }
        }
        return 1;
    }

    /**
     * c: check_cert
     */
    public int checkCertificate() throws Exception {
        X509CRL[] crl = new X509CRL[1];
        X509AuxCertificate x;
        int ok,cnum;
        cnum = errorDepth;
        x = chain.get(cnum);
        currentCertificate = x;
        ok = getCRL.call(this,crl,x);
        if(ok == 0) {
            error = X509Utils.V_ERR_UNABLE_TO_GET_CRL;
            ok = verifyCallback.call(new Integer(0), this);
            currentCRL = null;
            return ok;
        }
        currentCRL = crl[0];
        ok = checkCRL.call(this, crl[0]);
        if(ok == 0) {
            currentCRL = null;
            return ok;
        }
        ok = certificateCRL.call(this,crl[0],x);
        currentCRL = null;
        return ok;
    }

    /**
     * c: check_crl_time
     */
    public int checkCRLTime(X509CRL crl, int notify) throws Exception {
        currentCRL = crl;
        Date ptime = null;

        if((param.flags & X509Utils.V_FLAG_USE_CHECK_TIME) != 0) {
            ptime = this.param.checkTime;
        } else {
            ptime = Calendar.getInstance().getTime();
        }
        
        if(!crl.getThisUpdate().before(ptime)) {
            error=X509Utils.V_ERR_CRL_NOT_YET_VALID;
            if(notify == 0 || verifyCallback.call(new Integer(0),this) == 0) {
                return 0;
            }
        }
        if(crl.getNextUpdate() != null && !crl.getNextUpdate().after(ptime)) {
            error=X509Utils.V_ERR_CRL_HAS_EXPIRED;
            if(notify == 0 || verifyCallback.call(new Integer(0),this) == 0) {
                return 0;
            }
        }

        currentCRL = null;
        return 1;
    }

    /**
     * c: get_crl_sk
     */
    public int getCRLStack(X509CRL[] pcrl, Name nm, List<X509CRL> crls) throws Exception { 
        X509CRL best_crl = null;
        if(null != crls) {
            for(X509CRL crl : crls) {
                if(!nm.isEqual(crl.getIssuerX500Principal())) {
                    continue;
                }
                if(checkCRLTime(crl,0) != 0) {
                    pcrl[0] = crl;
                    return 1;
                }
                best_crl = crl;
            }
        }
        if(best_crl != null) {
            pcrl[0] = best_crl;
        }
        return 0;
    }

    /**
     * c: get_issuer_sk
     */
    public final static Store.GetIssuerFunction getIssuerStack = new Store.GetIssuerFunction() { 
            public int call(Object a1, Object a2, Object a3) throws Exception {
                X509AuxCertificate[] issuer = (X509AuxCertificate[])a1;
                StoreContext ctx = (StoreContext)a2;
                X509AuxCertificate x = (X509AuxCertificate)a3;
                issuer[0] = ctx.findIssuer(ctx.otherContext,x);
                if(issuer[0] != null) {
                    return 1;
                } else {
                    return 0;
                }
            }
        };

    /**
     * c: check_issued
     */
    public final static Store.CheckIssuedFunction defaultCheckIssued = new Store.CheckIssuedFunction() { 
            public int call(Object a1, Object a2, Object a3) throws Exception {
                StoreContext ctx = (StoreContext)a1;
                X509AuxCertificate x = (X509AuxCertificate)a2;
                X509AuxCertificate issuer = (X509AuxCertificate)a3;
                int ret = X509Utils.checkIfIssuedBy(issuer,x);
                if(ret == X509Utils.V_OK) {
                    return 1;
                }
                if((ctx.param.flags & X509Utils.V_FLAG_CB_ISSUER_CHECK) == 0) {
                    return 0;
                }
                ctx.error = ret;
                ctx.currentCertificate = x;
                ctx.currentIssuer = issuer;
                return ctx.verifyCallback.call(new Integer(0),ctx);
            }
        };

    /**
     * c: null_callback
     */
    public final static Store.VerifyCallbackFunction NullCallback = new Store.VerifyCallbackFunction() { 
            public int call(Object a1, Object a2) {
                return ((Integer)a1).intValue();
            }
        };

    /**
     * c: internal_verify
     */
    public final static Store.VerifyFunction internalVerify = new Store.VerifyFunction() { 
            public int call(Object a1) throws Exception {
                StoreContext ctx = (StoreContext)a1;
                Store.VerifyCallbackFunction cb = ctx.verifyCallback;
                int n = ctx.chain.size();
                ctx.errorDepth = n-1;
                n--;
                X509AuxCertificate xi = ctx.chain.get(n);
                X509AuxCertificate xs = null;
                int ok = 0;
                if(ctx.checkIssued.call(ctx,xi,xi) != 0) {
                    xs = xi;
                } else {
                    if(n<=0) {
                        ctx.error = X509Utils.V_ERR_UNABLE_TO_VERIFY_LEAF_SIGNATURE;
                        ctx.currentCertificate = xi;
                        ok = cb.call(new Integer(0),ctx);
                        return ok;
                    } else {
                        n--;
                        ctx.errorDepth = n;
                        xs = ctx.chain.get(n);
                    }
                }
                while(n>=0) {
                    ctx.errorDepth = n;
                    if(!xs.isValid()) {
                        try {
                            xs.verify(xi.getPublicKey());
                        } catch(Exception e) {
                            /*
                            System.err.println("n: " + n);
                            System.err.println("verifying: " + xs);
                            System.err.println("verifying with issuer?: " + xi);
                            System.err.println("verifying with issuer.key?: " + xi.getPublicKey());
                            System.err.println("exception: " + e);
                            */
                            ctx.error = X509Utils.V_ERR_CERT_SIGNATURE_FAILURE;
                            ctx.currentCertificate = xs;
                            ok = cb.call(new Integer(0),ctx);
                            if(ok == 0) {
                                return ok;
                            }
                        }
                    }
                    xs.setValid(true);
                    ok = ctx.checkCertificateTime(xs);
                    if(ok == 0) {
                        return ok;
                    }
                    ctx.currentIssuer = xi;
                    ctx.currentCertificate = xs;
                    ok = cb.call(new Integer(1),ctx);
                    if(ok == 0) {
                        return ok;
                    }
                    n--;
                    if(n>=0) {
                        xi = xs;
                        xs = ctx.chain.get(n);
                    }
                }
                ok = 1;
                return ok;
            }
        };

    /**
     * c: check_revocation
     */
    public final static Store.CheckRevocationFunction defaultCheckRevocation = new Store.CheckRevocationFunction() { 
            public int call(Object a1) throws Exception {
                StoreContext ctx = (StoreContext)a1;
                int last,ok=0;
                if((ctx.param.flags & X509Utils.V_FLAG_CRL_CHECK) == 0) {
                    return 1;
                }
                if((ctx.param.flags & X509Utils.V_FLAG_CRL_CHECK_ALL) != 0) {
                    last = ctx.chain.size() -1;
                } else {
                    last = 0;
                }
                for(int i=0;i<=last;i++) {
                    ctx.errorDepth = i;
                    ok = ctx.checkCertificate();
                    if(ok == 0) {
                        return 0;
                    }
                }
                return 1;
            }
        };

    /**
     * c: get_crl
     */
    public final static Store.GetCRLFunction defaultGetCRL = new Store.GetCRLFunction() { 
            public int call(Object a1, Object a2, Object a3) throws Exception {
                StoreContext ctx = (StoreContext)a1;
                X509CRL[] pcrl = (X509CRL[])a2;
                X509AuxCertificate x = (X509AuxCertificate)a3;
                Name nm = new Name(x.getIssuerX500Principal());
                X509CRL[] crl = new X509CRL[1];
                int ok = ctx.getCRLStack(crl,nm,ctx.crls);
                if(ok != 0) {
                    pcrl[0] = crl[0];
                    return 1;
                }
                X509Object[] xobj = new X509Object[1];
                ok = ctx.getBySubject(X509Utils.X509_LU_CRL,nm,xobj);
                if(ok == 0) {
                    if(crl[0] != null) {
                        pcrl[0] = crl[0];
                        return 1;
                    }
                    return 0;
                }
                pcrl[0] = (X509CRL)(((CRL)xobj[0]).crl);
                return 1;
            }
        };

    /**
     * c: check_crl
     */
    public final static Store.CheckCRLFunction defaultCheckCRL = new Store.CheckCRLFunction() { 
            public int call(Object a1, Object a2) throws Exception {
                StoreContext ctx = (StoreContext)a1;
                final X509CRL crl = (X509CRL)a2;
                X509AuxCertificate issuer = null;
                int ok = 0,chnum,cnum;
                cnum = ctx.errorDepth;
                chnum = ctx.chain.size()-1;
                if(cnum < chnum) {
                    issuer = ctx.chain.get(cnum+1);
                } else {
                    issuer = ctx.chain.get(chnum);
                    if(ctx.checkIssued.call(ctx,issuer,issuer) == 0) {
                        ctx.error = X509Utils.V_ERR_UNABLE_TO_GET_CRL_ISSUER;
                        ok = ctx.verifyCallback.call(new Integer(0),ctx);
                        if(ok == 0) {
                            return ok;
                        }
                    }
                }

                if(issuer != null) {
                    if(issuer.getKeyUsage() != null && !issuer.getKeyUsage()[6]) {
                        ctx.error = X509Utils.V_ERR_KEYUSAGE_NO_CRL_SIGN;
                        ok = ctx.verifyCallback.call(new Integer(0),ctx);
                        if(ok == 0) {
                            return ok;
                        }
                    }
                    final PublicKey ikey = issuer.getPublicKey();
                    if(ikey == null) {
                        ctx.error = X509Utils.V_ERR_UNABLE_TO_DECODE_ISSUER_PUBLIC_KEY;
                        ok = ctx.verifyCallback.call(new Integer(0),ctx);
                        if(ok == 0) {
                            return ok;
                        }
                    } else {
                        final boolean[] result = new boolean[1];
                        OpenSSLReal.doWithBCProvider(new Runnable() {
                                public void run() {
                                    try {
                                        crl.verify(ikey);
                                        result[0] = true;
                                    } catch(java.security.GeneralSecurityException e) {
                                        result[0] = false;
                                    }
                                }
                            });

                        if(!result[0]) {
                            ctx.error= X509Utils.V_ERR_CRL_SIGNATURE_FAILURE;
                            ok = ctx.verifyCallback.call(new Integer(0),ctx);
                            if(ok == 0) {
                                return ok;
                            }
                        }
                    }
                }

                ok = ctx.checkCRLTime(crl,1);
                if(ok == 0) {
                    return ok;
                }
                return 1;
            }
        };

    /**
     * c: cert_crl
     */
    public final static Store.CertificateCRLFunction defaultCertificateCRL = new Store.CertificateCRLFunction() { 
            public int call(Object a1, Object a2, Object a3) throws Exception {
                StoreContext ctx = (StoreContext)a1;
                X509CRL crl = (X509CRL)a2;
                X509AuxCertificate x = (X509AuxCertificate)a3;
                int ok;
                if(crl.getRevokedCertificate(x.getSerialNumber()) != null) {
                    ctx.error = X509Utils.V_ERR_CERT_REVOKED;
                    ok = ctx.verifyCallback.call(new Integer(0), ctx);
                    if(ok == 0) {
                        return 0;
                    }
                }
                if((ctx.param.flags & X509Utils.V_FLAG_IGNORE_CRITICAL) != 0) {
                    return 1;
                }

                if(crl.getCriticalExtensionOIDs() != null && crl.getCriticalExtensionOIDs().size()>0) {
                    ctx.error = X509Utils.V_ERR_UNHANDLED_CRITICAL_CRL_EXTENSION;
                    ok = ctx.verifyCallback.call(new Integer(0), ctx);
                    if(ok == 0) {
                        return 0;
                    }
                }
                return 1;
            }
        };

    /**
     * c: check_policy
     */
    public final static CheckPolicyFunction defaultCheckPolicy = new CheckPolicyFunction() { 
            public int call(Object a1) throws Exception {
                return 1;
            }
        };
}// X509_STORE_CTX
