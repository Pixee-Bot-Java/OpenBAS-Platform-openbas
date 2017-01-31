import * as schema from './Schema'
import {getReferential, fileDownload, putReferential, postReferential, delReferential} from '../utils/Action'

export const fetchAudiences = (exerciseId) => (dispatch) => {
  var uri = '/api/exercises/' + exerciseId + '/audiences'
  return getReferential(schema.arrayOfAudiences, uri)(dispatch)
}

export const downloadExportAudiences = (exerciseId) => (dispatch) => {
  return fileDownload('/api/exercises/' + exerciseId + '/audiences.xlsx')(dispatch)
}

export const downloadExportAudience = (exerciseId, audienceId) => (dispatch) => {
  return fileDownload('/api/exercises/' + exerciseId + '/audiences/' + audienceId + '/users.xlsx')(dispatch)
}

export const updateAudience = (exerciseId, audienceId, data) => (dispatch) => {
  var uri = '/api/exercises/' + exerciseId + '/audiences/' + audienceId
  return putReferential(schema.audience, uri, data)(dispatch)
}

export const addAudience = (exerciseId, data) => (dispatch) => {
  var uri = '/api/exercises/' + exerciseId + '/audiences'
  return postReferential(schema.audience, uri, data)(dispatch)
}

export const deleteAudience = (exerciseId, audienceId) => (dispatch) => {
  var uri = '/api/exercises/' + exerciseId + '/audiences/' + audienceId
  return delReferential(uri, 'audiences', audienceId)(dispatch)
}