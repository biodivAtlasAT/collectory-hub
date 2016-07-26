package au.org.ala.collectory

import au.org.ala.web.AuthService
import grails.converters.JSON
import org.apache.commons.httpclient.HttpClient
import org.apache.commons.httpclient.methods.DeleteMethod
import org.apache.commons.httpclient.methods.GetMethod
import org.apache.commons.httpclient.methods.HeadMethod
import org.apache.commons.httpclient.methods.PostMethod

class CollectoryHubService {
    def grailsApplication
    AuthService authService

    static final String DEFAULT_API_KEY_HEADER = "api_key"
    static final String USER_HEADER = "user"

    def isAddressEmpty(address) {
        if(address){
            return [address.street, address.postBox, address.city, address.state, address.postcode, address.country].every {!it}
        } else {
            true
        }
    }

    /**
     * Return all contacts for this group with the primary contact listed first filtered
     * to only include those with the 'public' attribute.
     *
     * @return list of ContactFor (contains the contact and the role for this collection)
     */
    List getPublicContactsPrimaryFirst(List contacts) {
        List list = contacts.findAll {it.contact.publish}
        if (list.size() > 1) {
            for (cf in list) {
                if (cf.primaryContact) {
                    // move it to the top
                    Collections.swap(list, 0, list.indexOf(cf))
                    break
                }
            }
        }
        return list
    }

    String buildName(Map contact) {
        if (contact.lastName)
            return [(contact.title ?: ''), (contact.firstName ?: ''), contact.lastName].join(" ").trim()
        else if (contact.email)
            return contact.email
        else if (contact.phone)
            return contact.phone
        else if (contact.mobile)
            return contact.mobile
        else if (contact.fax)
            return contact.fax
        else
            return ''
    }

    /**
     * List the uids that identify this institution and all its descendant institutions.
     *
     * @return list of UID
     */
    List<String> descendantUids(Map institution) {
        def uids = [institution.uid]
        if (institution.childInstitutions) {
            institution.childInstitutions.each {
                def child = _get(it as String)
                if (child) {
                    uids += child.descendantUids()
                }
            }
        }
        return uids
    }

    /**
     * List the data resource uids linked to this institution
     *
     * @return list of UID
     */
    List<String> descendantDataResources(Map institution) {
        def uids = []
        if (institution.linkedRecordProviders) {
            institution.linkedRecordProviders.each {
                uids.push(it.uid)
            }
        }
        return uids
    }

    /**
     * Do a post with JSON body
     * @param url
     * @param body - JSON string
     * @return
     */
    Map doPost(String url, String body){
        PostMethod post = new PostMethod(url);
        post.setRequestHeader("Authorization", grailsApplication.config.webservice.apiKey);
        post.setRequestBody(body);
        HttpClient httpClient = new HttpClient();
        int statusCode = httpClient.executeMethod(post);
        [status: statusCode, location: post.getResponseHeader('location')?.value, resp: post.responseBodyAsString]
    }

    /**
     * Post to a url with Map as the body parameter. This function adds authorization information to post body.
     * @param url
     * @param body
     * @return
     */
    Map doPost(String url, Map body){
        addAuthorisationFields(body)
        String json = (body as JSON).toString()
        doPost(url, json)
    }

    /**
     * add api key and user email for authorisation and audit activities in collectory
     * @param body
     */
    public void addAuthorisationFields(Map body) {
        body[DEFAULT_API_KEY_HEADER] = grailsApplication.config.webservice.apiKey
        body[USER_HEADER] = authService.getEmail()
        body
    }

    /**
     * This function does a http HEAD call on the given url.
     * @param url
     * @return
     */
    public Map doHead(String url) {
        HeadMethod head = new HeadMethod(url);
        HttpClient httpClient = new HttpClient();
        int statusCode = httpClient.executeMethod(head);
        [status: statusCode]
    }

    /**
     * This function does a http HEAD call on the given url.
     * @param url
     * @return
     */
    public int doDelete(String url) {
        DeleteMethod delete = new DeleteMethod(url);
        HttpClient httpClient = new HttpClient();
        httpClient.executeMethod(delete);
    }

    /**
     * Do a get request. This method returns headers as well.
     * @param url
     * @return
     */
    public Map doGet(String url){
        Map result = [:]
        GetMethod get = new GetMethod(url);
        HttpClient httpClient = new HttpClient();
        result.status = httpClient.executeMethod(get);
        result.headers = get.getResponseHeaders()
        result.resp = get.responseBodyAsString

        result
    }
}
